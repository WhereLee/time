package com.reason.modules.device.mq;

import com.alibaba.fastjson2.JSON;
import com.reason.common.exception.RRException;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.config.DeviceChannelAuthenticator;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceEventForm;
import com.reason.modules.device.service.DeviceEventService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 设备事件 MQ 消费者（阶段2：事件走 RocketMQ，与 HTTP 双写并行——D6）
 *
 * <p>复用铁律（规格 §5-2）：验签直接调 {@link DeviceChannelAuthenticator#authenticateEvent}、
 * 编排直接调 {@link DeviceEventService#handleStateEvent}——消费路径不绕开、不重写既有逻辑。</p>
 *
 * <p>并发度=1（D5）：单消费线程逐条 receive→处理→ack；topic device-event 单队列全局有序。
 * 平台序守卫要求事件按 eventSeq 单调到达——乱序迟到的真事件会被当旧序拒（=丢数据），
 * 故本阶段不引入任何并发消费。</p>
 *
 * <p>失败分级（协议 v2 §5 错误语义在 MQ 侧的映射，规格 C1）：</p>
 * <ul>
 *   <li>验签失败/未登记设备/协议垃圾（HTTP 路径 4xx 同语义）→ <b>ack 丢弃 + error 日志</b>
 *       （毒消息不入重投循环——防无限重投刷 broker）；</li>
 *   <li>业务处理抛瞬时异常（DB 抖动等）→ <b>不 ack</b>，broker 到期重投；重投耗尽
 *       （消费组 retryMaxTimes=3，A4 设定）进死信 %DLQ%platform-device-event；</li>
 *   <li>重复投递由业务幂等天然吸收（台账序守卫 + 按 seq 精确销账）——consumer 不做额外去重。</li>
 * </ul>
 *
 * <p>broker 故障韧性：consumer 懒构建 + 失败重建（5s 节拍）——broker 未就绪/重启不炸平台
 * （D3 收益前提：broker 故障时心跳判活等其余链路必须活着）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "reason.device.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeviceEventMqConsumer {

    /** 消费处理结论（ACK=签收不再重投；RETRY=不签收，等 broker 重投） */
    enum Outcome {
        ACK, RETRY
    }

    /** 消息不可见时长：receive 后的处理窗口，超时未 ack broker 自动重投（也是瞬时故障的重投间隔） */
    private static final Duration INVISIBLE_DURATION = Duration.ofSeconds(15);

    /** 长轮询等待时长（空队列时 receive 阻塞上限；同时是停机信号的最长感知延迟） */
    private static final Duration AWAIT_DURATION = Duration.ofSeconds(5);

    /** consumer 重建节拍（broker 未就绪不炸平台，记 error 后持续重试） */
    private static final long REBUILD_INTERVAL_MILLIS = 5000;

    private final DeviceChannelProperties properties;
    private final DeviceChannelAuthenticator authenticator;
    private final DeviceEventService deviceEventService;
    private final ClientServiceProvider provider = ClientServiceProvider.loadService();

    /** 运行标志（@PreDestroy 置 false：循环退出，consumer 关闭） */
    private volatile boolean running = true;

    /** consumer 懒构建/重建（消费线程内访问；broker 故障时置 null 下轮重建） */
    private SimpleConsumer consumer;

    /** 单消费线程（D5 并发度=1 保序） */
    private Thread consumerThread;

    public DeviceEventMqConsumer(DeviceChannelProperties properties,
                                 DeviceChannelAuthenticator authenticator,
                                 DeviceEventService deviceEventService) {
        this.properties = properties;
        this.authenticator = authenticator;
        this.deviceEventService = deviceEventService;
    }

    @PostConstruct
    public void start() {
        consumerThread = new Thread(this::consumeLoop, "platform-mq-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("[MQ事件消费] 消费线程已启动 endpoint={} topic={} group={}（并发度=1 保序——D5）",
                properties.getMq().getEndpoint(), properties.getMq().getTopic(), properties.getMq().getConsumerGroup());
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        closeConsumerQuietly();
    }

    /**
     * 消费主循环（单线程）：懒构建 consumer → 长轮询 receive → 逐条处理 → 按分级结论 ack/不 ack。
     * 任何意外异常不得打爆消费线程（最外层 catch + 节拍自愈）
     */
    private void consumeLoop() {
        while (running) {
            try {
                SimpleConsumer c = ensureConsumer();
                if (c == null) {
                    sleep(REBUILD_INTERVAL_MILLIS);
                    continue;
                }
                List<MessageView> messages = c.receive(1, INVISIBLE_DURATION);
                for (MessageView message : messages) {
                    if (!running) {
                        //停机窗口：未 ack 的消息由 broker 重投（平台重启窗口零丢失的来源）
                        return;
                    }
                    handleOne(c, message);
                }
            } catch (ClientException e) {
                //receive 失败（broker 抖动/连接断/重启）：重建 consumer 下轮再试
                if (running) {
                    log.error("[MQ事件消费] receive 异常，{}ms 后重建 consumer: {}", REBUILD_INTERVAL_MILLIS, e.getMessage());
                }
                closeConsumerQuietly();
                sleep(REBUILD_INTERVAL_MILLIS);
            } catch (Exception e) {
                if (running) {
                    log.error("[MQ事件消费] 消费循环意外异常（{}ms 后继续）", REBUILD_INTERVAL_MILLIS, e);
                }
                sleep(REBUILD_INTERVAL_MILLIS);
            }
        }
    }

    /**
     * 处理单条消息：process 出分级结论 → ACK 路径签收；RETRY 路径不签收等 broker 重投
     */
    private void handleOne(SimpleConsumer c, MessageView message) {
        Outcome outcome;
        try {
            outcome = process(message);
        } catch (Exception e) {
            //最外层兜底：意外异常按瞬时故障处理（重投→耗尽进死信），不得打爆消费线程
            log.error("[MQ事件消费] 处理意外异常，按瞬时故障等 broker 重投 msgId={}", message.getMessageId(), e);
            outcome = Outcome.RETRY;
        }
        if (outcome == Outcome.ACK) {
            try {
                c.ack(message);
            } catch (Exception e) {
                //ack 失败 = broker 将重投——业务幂等吸收（台账序守卫+按 seq 销账），只留痕不补偿
                log.error("[MQ事件消费] ack 失败，消息将被 broker 重投（业务幂等吸收）msgId={} cause={}",
                        message.getMessageId(), e.getMessage());
            }
        }
        //RETRY：不 ack，INVISIBLE_DURATION 到期 broker 重投；重投耗尽（retryMaxTimes=3）进死信
    }

    /**
     * 消费处理（分级决策与 IO 分离，包私有可见性供单测注入消息对象——规格 F1）：
     * 验签（复用 DeviceChannelAuthenticator）→ 编排（复用 DeviceEventService）
     */
    Outcome process(MessageView message) {
        //1. 解析信封：body=协议 v2 事件 JSON（与 HTTP body 同构）；签名/设备号在 message property
        Map<String, String> props = message.getProperties();
        String propDeviceNo = props.get("X-Device-No");
        String signature = props.get("X-Device-Sign");
        DeviceEventForm form;
        try {
            ByteBuffer body = message.getBody().duplicate();
            byte[] bytes = new byte[body.remaining()];
            body.get(bytes);
            form = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), DeviceEventForm.class);
        } catch (Exception e) {
            log.error("[MQ事件消费-毒消息丢弃] 消息体非协议v2事件JSON（不重投）msgId={} cause={}",
                    message.getMessageId(), e.getMessage());
            return Outcome.ACK;
        }
        //必填校验（协议 v2 §3.1：commandSeq 可空，其余必填）——缺字段=协议垃圾，毒消息丢弃
        if (form == null || form.getDeviceNo() == null || form.getDeviceNo().isEmpty()
                || form.getState() == null || form.getBootId() == null || form.getBootId().isEmpty()
                || form.getEventSeq() == null) {
            log.error("[MQ事件消费-毒消息丢弃] 缺必填字段(deviceNo/state/bootId/eventSeq)（不重投）msgId={}",
                    message.getMessageId());
            return Outcome.ACK;
        }
        //信封完整性：property 设备号与报文体必须一致（签名以报文体字段为 canonical——与 HTTP 路径同一语义）
        if (!form.getDeviceNo().equals(propDeviceNo)) {
            log.error("[MQ事件消费-毒消息丢弃] property X-Device-No 与报文体 deviceNo 不一致（不重投）msgId={} prop={} body={}",
                    message.getMessageId(), propDeviceNo, form.getDeviceNo());
            return Outcome.ACK;
        }
        //2. 验签 + 状态码合法性（复用 DeviceChannelAuthenticator，勿重写；HTTP 路径 4xx 同语义）
        try {
            authenticator.authenticateEvent(form.getDeviceNo(), form.getState(), form.getCommandSeq(),
                    form.getBootId(), form.getEventSeq(), signature);
            DeviceState.fromCode(form.getState());
        } catch (ResponseStatusException | IllegalArgumentException e) {
            log.error("[MQ事件消费-毒消息丢弃] 验签/协议校验失败（不重投）deviceNo={} msgId={} cause={}",
                    form.getDeviceNo(), message.getMessageId(), e.getMessage());
            return Outcome.ACK;
        }
        //3. 事件语义编排（复用 DeviceEventServiceImpl，勿重写：序守卫/销账/FAULT 中断/告警全在其中）
        try {
            deviceEventService.handleStateEvent(form);
        } catch (RRException e) {
            //未登记设备等配置错位（HTTP 路径 400 同语义）= 毒消息丢弃，不入重投循环
            log.error("[MQ事件消费-毒消息丢弃] 业务拒绝（不重投）deviceNo={} msgId={} msg={}",
                    form.getDeviceNo(), message.getMessageId(), e.getMessage());
            return Outcome.ACK;
        } catch (Exception e) {
            //DB 瞬时故障等：不 ack 等 broker 重投（重投耗尽进死信）
            log.error("[MQ事件消费-等重投] 业务处理瞬时异常 deviceNo={} eventSeq={} msgId={} cause={}",
                    form.getDeviceNo(), form.getEventSeq(), message.getMessageId(), e.getMessage());
            return Outcome.RETRY;
        }
        //D6 留痕：双写期 HTTP 路径并行处理同事件，由业务幂等吸收（序守卫拒绝=正常对照痕迹）
        log.info("[MQ事件消费] deviceNo={} eventSeq={} bootId={} commandSeq={} state={}"
                        + "（双写期：HTTP 路径并行处理同事件由业务幂等吸收）",
                form.getDeviceNo(), form.getEventSeq(), form.getBootId(), form.getCommandSeq(), form.getState());
        return Outcome.ACK;
    }

    /**
     * consumer 懒构建（broker 未就绪不炸平台——记 error，下节拍重试）；
     * 订阅 topic=device-event，消费组 platform-device-event，单线程 receive 即并发度=1（D5）
     */
    private SimpleConsumer ensureConsumer() {
        if (consumer != null) {
            return consumer;
        }
        try {
            ClientConfiguration config = ClientConfiguration.newBuilder()
                    .setEndpoints(properties.getMq().getEndpoint())
                    .build();
            consumer = provider.newSimpleConsumerBuilder()
                    .setClientConfiguration(config)
                    .setConsumerGroup(properties.getMq().getConsumerGroup())
                    .setSubscriptionExpressions(Collections.singletonMap(
                            properties.getMq().getTopic(), FilterExpression.SUB_ALL))
                    .setAwaitDuration(AWAIT_DURATION)
                    .build();
            log.info("[MQ事件消费] consumer 已就绪 endpoint={} topic={} group={}",
                    properties.getMq().getEndpoint(), properties.getMq().getTopic(),
                    properties.getMq().getConsumerGroup());
        } catch (Exception e) {
            log.error("[MQ事件消费] consumer 构建失败（broker 未就绪？），{}ms 后重试: {}",
                    REBUILD_INTERVAL_MILLIS, e.getMessage());
        }
        return consumer;
    }

    private void closeConsumerQuietly() {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (Exception e) {
                log.warn("[MQ事件消费] consumer 关闭异常: {}", e.getMessage());
            }
            consumer = null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
