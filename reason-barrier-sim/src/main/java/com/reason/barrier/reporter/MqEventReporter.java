package com.reason.barrier.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reason.barrier.config.DeviceSignature;
import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.BarrierState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * MQ 状态上报（阶段2 双写：事件经 RocketMQ 投递，与 HTTP 通道并行——D6 双写期）
 *
 * <p>信封契约（contracts/PROTOCOL-V2.md §7）：topic=device-event；消息体=协议 v2 事件 JSON
 * （与 HTTP body 完全同构：deviceNo/state/commandSeq/bootId/eventSeq，commandSeq 可空）；
 * 签名与设备号置 message property：X-Device-No / X-Device-Sign（canonical 不变，§3.1 同一拼法）。</p>
 *
 * <p>保序铁律（D5）：单发送线程 + 单队列 topic——乱序事件会被平台序守卫当旧序拒（=丢数据）。
 * 所有事件进有界队列，由唯一发送线程按序 drain；队列内顺序在任何故障路径下不得乱。</p>
 *
 * <p>broker 故障不丢（验收硬指标）：发送失败 → 退避重试 1s/2s/3s…上限 5 次/条 → 仍失败则
 * 事件持留队首、按 5s 节拍持续等待 broker 恢复（规格的"保留在队列尾部"按 D5 保序落实为队首持留：
 * 失败事件若让位重排，后续更大 eventSeq 先到平台，本事件恢复后反被序守卫当旧序拒=真丢数据），
 * 恢复后按序补发；队列满则丢弃最旧未发事件（记 error，平台 QUERY_STATE/对账兜底——
 * 分层可靠性，与 HTTP 通道"重试耗尽丢弃交兜底"同哲学）。</p>
 *
 * <p>producer 懒重建：broker 晚于本进程就绪/启动失败不炸应用——首次发送时才构建，
 * 构建失败由重试循环兜底（等价于"发送重试时懒重建"，启动期零网络依赖）。</p>
 *
 * <p>网络剧本（NetworkCondition）不作用于本通道：它模拟的是 sim→平台 的不可靠直连链路；
 * MQ 通道的故障剧本由真实停起 broker 制造（F3 剧本），不叠加应用层注入。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sim.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqEventReporter implements EventReporter {

    /** 每条事件的快速退避重试上限（1s/2s/3s/4s/5s）；超限转入"等 broker 恢复"模式（5s 节拍持续重试，不丢弃） */
    private static final int MAX_FAST_RETRY = 5;

    /** 关闭时剩余队列尽力补发的总时间预算（不停机久等——双写期 HTTP 已并行投递，平台对账兜底） */
    private static final long SHUTDOWN_FLUSH_BUDGET_MILLIS = 5000;

    private final SimProperties properties;

    /** 信封序列化（HashMap 允许 commandSeq=null，与 HTTP body 同构；sim 侧无 fastjson2，用 web 自带 Jackson） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ClientServiceProvider provider = ClientServiceProvider.loadService();

    /** 失败缓冲队列（有界，容量 sim.mq.buffer-size 默认 500） */
    private final BlockingQueue<PendingEvent> queue;

    /** 单发送线程（保序铁律：与 HttpEventReporter 同构，D5 同源约束） */
    private final ExecutorService sender;

    /** producer 懒重建（volatile：发送线程构建/持写，关闭线程读） */
    private volatile Producer producer;

    /** 运行标志（@PreDestroy 置 false：拒收新事件，在途重试退出，剩余队列尽力补发一次） */
    private volatile boolean running = true;

    @Autowired
    public MqEventReporter(SimProperties properties) {
        this(properties, null);
    }

    /**
     * 测试接缝：注入 fake producer 断言信封构造/重试保序（不连 broker）
     */
    MqEventReporter(SimProperties properties, Producer producer) {
        this.properties = properties;
        this.producer = producer;
        this.queue = new ArrayBlockingQueue<>(properties.getMq().getBufferSize());
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "barrier-mq-producer");
            t.setDaemon(true);
            return t;
        };
        this.sender = Executors.newSingleThreadExecutor(tf);
        this.sender.submit(this::drainLoop);
    }

    @Override
    public void report(String deviceNo, BarrierState state, Long commandSeq, String bootId, long eventSeq) {
        if (!running) {
            log.warn("[MQ上报丢弃] 关闭中 deviceNo={} state={} seq={} eventSeq={}（双写期 HTTP 通道并行在投）",
                    deviceNo, state, commandSeq, eventSeq);
            return;
        }
        PendingEvent event = new PendingEvent(deviceNo, state, commandSeq, bootId, eventSeq);
        synchronized (queue) {
            while (!queue.offer(event)) {
                //队列满：丢弃最旧未发事件为新事件腾位（记 error——平台 QUERY_STATE/心跳对账兜底）
                PendingEvent dropped = queue.poll();
                if (dropped == null) {
                    break;
                }
                log.error("[MQ缓冲溢出-丢弃最旧] deviceNo={} state={} seq={} eventSeq={}（容量={}，平台 QUERY_STATE 兜底）",
                        dropped.deviceNo(), dropped.state(), dropped.commandSeq(), dropped.eventSeq(),
                        properties.getMq().getBufferSize());
            }
        }
    }

    /**
     * 发送主循环（唯一发送线程）：严格按队列顺序逐条发送——保序不在此层做任何"优化"
     */
    private void drainLoop() {
        while (running) {
            PendingEvent event;
            try {
                event = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            sendWithRetry(event);
        }
    }

    /**
     * 单条发送：退避重试 → 超限转"等 broker 恢复"（队首持留，5s 节拍，永不因故障丢弃——丢弃只发生在队列满时）
     */
    private void sendWithRetry(PendingEvent event) {
        Message message = buildMessage(event);
        if (message == null) {
            //配置错位（密钥缺失）= 毒事件：重试无意义，丢弃立即可见（与 HTTP 通道同语义）
            return;
        }
        int attempt = 0;
        while (running) {
            try {
                ensureProducer().send(message);
                log.info("[MQ上报成功] deviceNo={} state={} seq={} eventSeq={}",
                        event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq());
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt <= MAX_FAST_RETRY) {
                    log.warn("[MQ上报失败-将第{}次重试] deviceNo={} state={} seq={} eventSeq={} cause={}",
                            attempt, event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq(), e.getMessage());
                } else if (attempt == MAX_FAST_RETRY + 1) {
                    log.error("[MQ上报转入待恢复] broker 疑似不可用，事件持留队首按 5s 节拍等待恢复"
                                    + "（D5 保序：本事件不发送成功，后续事件不越过）deviceNo={} eventSeq={}",
                            event.deviceNo(), event.eventSeq());
                }
                backoff(attempt);
            }
        }
        log.warn("[MQ上报丢弃] 关闭窗口放弃在途事件 deviceNo={} state={} eventSeq={}（双写期 HTTP 通道并行在投）",
                event.deviceNo(), event.state(), event.eventSeq());
    }

    /**
     * 信封构造（协议 v2 §3.1 + §7：报文体同构 HTTP body；签名进 message property，canonical 不变）
     *
     * @return 消息；null=密钥缺失（配置错位，不重试）
     */
    private Message buildMessage(PendingEvent event) {
        String secret = properties.secretOf(event.deviceNo());
        if (secret == null || secret.isEmpty()) {
            log.error("[MQ上报取消] 设备未配置密钥 deviceNo={}（联调需环境变量注入）", event.deviceNo());
            return null;
        }
        //HashMap：commandSeq 可空（外力改态事件），Map.of 不允许 null——与 HTTP body 完全同构
        Map<String, Object> body = new HashMap<>();
        body.put("deviceNo", event.deviceNo());
        body.put("state", event.state().getCode());
        body.put("commandSeq", event.commandSeq());
        body.put("bootId", event.bootId());
        body.put("eventSeq", event.eventSeq());
        try {
            return provider.newMessageBuilder()
                    .setTopic(properties.getMq().getTopic())
                    .setBody(objectMapper.writeValueAsBytes(body))
                    //0.5 签名进 message property（MQ 适配层：canonical 与 HTTP 同一拼法）
                    .addProperty("X-Device-No", event.deviceNo())
                    .addProperty("X-Device-Sign", DeviceSignature.sign(secret,
                            DeviceSignature.canonicalEvent(event.deviceNo(), event.state().getCode(),
                                    event.commandSeq(), event.bootId(), event.eventSeq())))
                    //keys 留排查锚点：broker 侧可按 设备-事件序 检索消息
                    .setKeys(event.deviceNo() + "-" + event.eventSeq())
                    .build();
        } catch (Exception e) {
            //序列化失败只可能是代码 bug（字段均基本类型）——按毒事件丢弃，不进重试循环
            log.error("[MQ上报取消] 信封构造失败 deviceNo={} eventSeq={}", event.deviceNo(), event.eventSeq(), e);
            return null;
        }
    }

    /**
     * producer 懒重建：首次发送/broker 就绪延迟时不炸应用；构建失败抛异常由重试循环兜底
     */
    private Producer ensureProducer() throws Exception {
        Producer p = producer;
        if (p == null) {
            synchronized (this) {
                if (producer == null) {
                    ClientConfiguration config = ClientConfiguration.newBuilder()
                            .setEndpoints(properties.getMq().getEndpoint())
                            .build();
                    producer = provider.newProducerBuilder()
                            .setClientConfiguration(config)
                            .setTopics(properties.getMq().getTopic())
                            .build();
                    log.info("[MQ] producer 已就绪 endpoint={} topic={}",
                            properties.getMq().getEndpoint(), properties.getMq().getTopic());
                }
                p = producer;
            }
        }
        return p;
    }

    /**
     * 退避：1s/2s/3s/4s/5s，超限后恒 5s（等 broker 恢复节拍）
     */
    private void backoff(int attempt) {
        try {
            Thread.sleep(1000L * Math.min(attempt, MAX_FAST_RETRY));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 优雅关闭（B3）：停收新事件 → 队列剩余在时间预算内尽力补发一次（不重试）→
     * 超时/失败记 error 丢弃（取舍：不为补发阻塞停机；双写期 HTTP 已并行投递，平台 QUERY_STATE 兜底）
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        sender.shutdownNow();
        long deadline = System.currentTimeMillis() + SHUTDOWN_FLUSH_BUDGET_MILLIS;
        int flushed = 0;
        int dropped = 0;
        PendingEvent event;
        while ((event = queue.poll()) != null) {
            if (System.currentTimeMillis() > deadline) {
                dropped++;
                continue;
            }
            try {
                Message message = buildMessage(event);
                if (message != null) {
                    ensureProducer().send(message);
                    flushed++;
                } else {
                    dropped++;
                }
            } catch (Exception e) {
                dropped++;
                log.error("[MQ关闭补发失败] deviceNo={} eventSeq={} cause={}",
                        event.deviceNo(), event.eventSeq(), e.getMessage());
            }
        }
        if (flushed > 0 || dropped > 0) {
            log.info("[MQ关闭] 剩余队列补发={} 丢弃={}（丢弃部分由平台 QUERY_STATE/对账兜底）", flushed, dropped);
        }
        Producer p = producer;
        if (p != null) {
            try {
                p.close();
            } catch (Exception e) {
                log.warn("[MQ关闭] producer 关闭异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 排队中的待发送事件（保序队列元素）
     */
    private record PendingEvent(String deviceNo, BarrierState state, Long commandSeq, String bootId, long eventSeq) {
    }
}
