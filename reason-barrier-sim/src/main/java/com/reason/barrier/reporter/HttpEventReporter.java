package com.reason.barrier.reporter;

import com.reason.barrier.config.DeviceSignature;
import com.reason.barrier.config.SimProperties;
import com.reason.barrier.config.TraceIds;
import com.reason.barrier.model.BarrierState;
import com.reason.barrier.network.NetworkCondition;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * HTTP 状态上报（走平台设备事件通道，携 per-device HMAC 签名；协议 v2 事件载荷）
 *
 * <p>上报异步化（P6）：动作线程只做机械耗时，通信交给独立发送线程——上报失败重试不会拉长
 * 机械时钟（"上报在动作线程内同步阻塞"是模拟器作为负载源时的时间失真源，一并修掉）。</p>
 *
 * <p>失败退避重试（协议 v2 §3.1/逻辑机制清单 P6）：事件丢失率≈网络失败率不可接受——
 * 平台靠 eventSeq 幂等去重，重发无害。重试耗尽仍失败则丢弃并记 error（分层：尽力发送 +
 * 平台侧 QUERY_STATE/对账兜底，见平台 BarrierMonitorTask）。</p>
 *
 * <p>网络剧本（NetworkCondition）：上行方向断/单次丢包在此生效——模拟"十公里外"的
 * 不可靠链路，故障可本地制造、可进自动化剧本。批次2 B8 补全：事件延迟（打 grace 边界）/
 * 重放（成功送达后同报文重发）/ 乱序（同设备两条倒序发）——全部只作用于本 HTTP 通道，
 * MQ 通道故障剧本=真实停起 broker（注入器哲学不破）。</p>
 */
@Slf4j
@Component
public class HttpEventReporter implements EventReporter {

    /** 上报重试上限（协议 v2 P6：退避 1s/2s/3s，超限丢弃交平台兜底） */
    private static final int MAX_RETRY = 3;

    /** 乱序窗口（批次2 B8）：集齐该设备 2 条事件按 eventSeq 降序发送 */
    private static final int REORDER_WINDOW_SIZE = 2;

    /** 乱序窗口超时（只来 1 条时原序放行——防"布防后只发生一次动作"剧本卡死发送线程） */
    private static final long REORDER_WINDOW_TIMEOUT_MILLIS = 5000;

    private final SimProperties properties;
    private final NetworkCondition network;
    private final RestTemplate restTemplate;
    private final ExecutorService sender;

    /** 乱序窗口缓冲（单发送线程内访问，天然无并发问题；发送期间线程被占，集齐判定不看队列占位） */
    private PendingEvent reorderFirst;
    private long reorderFirstAt;

    /**
     * Spring 装配入口（多构造器必须显式 @Autowired——否则 Spring 找不到默认构造起不来，
     * 阶段2 同类启动期坑复现：测试接缝 package-private 构造器不参与装配）
     */
    @Autowired
    public HttpEventReporter(SimProperties properties, NetworkCondition network) {
        this(properties, network, null);
    }

    /**
     * 测试接缝（批次2 F1）：注入 RestTemplate 断言发送行为（主构造 HTTP 客户端配置不变——
     * JDK HttpClient 连接复用 + 超时参数化）
     */
    HttpEventReporter(SimProperties properties, NetworkCondition network, RestTemplate restTemplate) {
        this.properties = properties;
        this.network = network;
        if (restTemplate != null) {
            this.restTemplate = restTemplate;
        } else {
            //P5：JDK HttpClient 连接复用（不再每次请求新建 TCP 连接）+ 超时参数化入 yml 可标定
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
            this.restTemplate = new RestTemplate(factory);
        }
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "barrier-event-sender");
            t.setDaemon(true);
            return t;
        };
        //单发送线程保序：事件携带递增 eventSeq，平台序守卫会拒绝乱序的迟到真事件（D5 同源约束）
        this.sender = Executors.newSingleThreadExecutor(tf);
    }

    @Override
    public void report(String deviceNo, BarrierState state, Long commandSeq, String bootId, long eventSeq, String traceId) {
        //异步发送：动作线程不阻塞在网络上（机械时间与通信时间解耦）；traceId 随任务显式传递
        sender.submit(() -> sendWithRetry(new PendingEvent(deviceNo, state, commandSeq, bootId, eventSeq, traceId)));
    }

    private void sendWithRetry(PendingEvent event) {
        //发送线程独立于动作线程：MDC 不自动传递，显式置入使上报日志可被 traceId 串联
        MDC.put(TraceIds.MDC_KEY, event.traceId());
        try {
            doSendWithRetry(event);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    private void doSendWithRetry(PendingEvent event) {
        //网络剧本：上行方向断/单次丢包——模拟事件丢失（重试剧本：丢一次后恢复，重试应成功）
        if (network.shouldDropEvent()) {
            log.warn("[网络剧本] 事件上报被丢弃 deviceNo={} state={} seq={} eventSeq={}",
                    event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq());
            return;
        }

        //网络剧本（批次2 B8）：事件发送前人为延迟——打心跳对账 grace 边界（只发生在发送线程，动作线程零阻塞）
        long eventDelay = network.getEventDelayMillis();
        if (eventDelay > 0) {
            log.warn("[网络剧本] 事件被延迟 {}ms deviceNo={} state={} seq={} eventSeq={}",
                    eventDelay, event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq());
            try {
                Thread.sleep(eventDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        //网络剧本（批次2 B8）：乱序窗口——布防设备的事件进窗口缓冲，集齐 2 条按 eventSeq 降序发送；
        //只来 1 条超时 5s 原序放行；缓冲期间其他设备事件照常发送（乱序跨设备无业务意义，序守卫 per-device）
        String reorderTarget = network.getReorderNextDeviceNo();
        if (reorderTarget != null && reorderTarget.equals(event.deviceNo())) {
            if (reorderFirst == null) {
                reorderFirst = event;
                reorderFirstAt = System.currentTimeMillis();
                return;
            }
            if (event.eventSeq() > reorderFirst.eventSeq()) {
                //集齐 2 条：eventSeq 降序（先发新事件 e2，首条 e1 随后——e2 先达受理、e1 迟到被序守卫拒）
                flushReorder(event);
                return;
            }
            //布防窗口内 eventSeq 倒退（重启/异常序列）：退化为逐条直发（不在本剧本范围）
            log.warn("[网络剧本] 乱序窗口内 eventSeq 倒退（首条={} 当前={}），直发不进窗口 deviceNo={}",
                    reorderFirst.eventSeq(), event.eventSeq(), event.deviceNo());
        } else if (reorderFirst != null
                && System.currentTimeMillis() - reorderFirstAt >= REORDER_WINDOW_TIMEOUT_MILLIS) {
            //窗口超时才被下一条其他事件唤醒：原序放行首条（防"布防后只发生一次动作"剧本卡死通道），
            //本事件随后正常处理——其他设备事件不因乱序布防被拖住
            log.warn("[网络剧本] 乱序窗口超时 {}ms 未集齐，首条原序放行 deviceNo={} eventSeq={}",
                    REORDER_WINDOW_TIMEOUT_MILLIS, reorderFirst.deviceNo(), reorderFirst.eventSeq());
            flushReorder(null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //0.5 per-device HMAC：不再携共享口令——平台按 X-Device-No 查密钥验签
        headers.set("X-Device-No", event.deviceNo());
        //批次1 TraceId 贯穿：平台 TraceIdFilter 吃入站 X-Trace-Id 置 MDC——上报日志与平台处理日志同号
        headers.set(TraceIds.HEADER, event.traceId());
        String secret = properties.secretOf(event.deviceNo());
        if (secret == null || secret.isEmpty()) {
            log.error("[上报取消] 设备未配置密钥 deviceNo={}（联调需环境变量注入）", event.deviceNo());
            return;
        }
        headers.set("X-Device-Sign",
                DeviceSignature.sign(secret, DeviceSignature.canonicalEvent(event.deviceNo(), event.state().getCode(),
                        event.commandSeq(), event.bootId(), event.eventSeq())));
        //HashMap：commandSeq 可空（外力改态事件），Map.of 不允许 null
        Map<String, Object> body = new HashMap<>();
        body.put("deviceNo", event.deviceNo());
        body.put("state", event.state().getCode());
        body.put("commandSeq", event.commandSeq());
        body.put("bootId", event.bootId());
        body.put("eventSeq", event.eventSeq());

        boolean delivered = sendOnceWithRetry(event, body, headers);

        //网络剧本（批次2 B8）：重放——成功送达（含重试成功）后消费布防，命中则同报文（同 body/同签名头/
        //同 traceId）再发 1 次；重发失败按普通发送失败处理（走一次退避，不递归重放）；
        //失败（重试耗尽丢弃/4xx 拒绝）不消费布防——不放大故障，重放剧本需要"真送达后再重发"
        if (delivered && network.consumeReplay()) {
            log.warn("[网络剧本] 事件重放：同报文重发 1 次 deviceNo={} state={} seq={} eventSeq={}",
                    event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq());
            sendOnceWithRetry(event, body, headers);
        }
    }

    /**
     * 乱序窗口排空（一次性开关随窗口完成自动复位）：先发 later（eventSeq 更大者），再发窗口首条——
     * 乱序倒发在此发生；later=null 表示超时放行（只发首条原序）
     */
    private void flushReorder(PendingEvent later) {
        PendingEvent first = reorderFirst;
        reorderFirst = null;
        network.clearReorder();
        if (later != null) {
            log.warn("[网络剧本] 乱序窗口集齐：eventSeq 降序发送（先 {} 后 {}）deviceNo={}",
                    later.eventSeq(), first.eventSeq(), first.deviceNo());
            doSendWithRetry(later);
        }
        doSendWithRetry(first);
    }

    /**
     * 单条发送（退避 1s/2s/3s，上限 MAX_RETRY）：4xx 拒绝不重试、重试耗尽丢弃交平台兜底
     *
     * @return true=成功送达（消费重放布防的依据）
     */
    private boolean sendOnceWithRetry(PendingEvent event, Map<String, Object> body, HttpHeaders headers) {
        int attempt = 0;
        while (true) {
            try {
                restTemplate.postForEntity(properties.getEventUrl(), new HttpEntity<>(body, headers), String.class);
                log.info("[上报成功] deviceNo={} state={} seq={} eventSeq={}",
                        event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq());
                return true;
            } catch (HttpStatusCodeException e) {
                //0.7：平台 4xx = 平台明确拒绝（签名无效 401/协议错误 400）——重试无意义，立即放弃；
                //token 漂移/密钥失配在此立即可见（不再"只看 HTTP 状态记上报成功"）
                HttpStatusCode status = e.getStatusCode();
                log.error("[上报被平台拒绝-不重试] deviceNo={} state={} seq={} http={} resp={}",
                        event.deviceNo(), event.state(), event.commandSeq(), status.value(), e.getResponseBodyAsString());
                return false;
            } catch (RestClientException e) {
                attempt++;
                if (attempt > MAX_RETRY) {
                    //重试耗尽：事件本次丢失（平台侧由状态查询/对账兜底——分层可靠性，不是无限重试）
                    log.error("[上报失败-重试耗尽] deviceNo={} state={} seq={} eventSeq={} cause={}",
                            event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq(), e.getMessage());
                    return false;
                }
                log.warn("[上报失败-将第{}次重试] deviceNo={} state={} seq={} eventSeq={} cause={}",
                        attempt, event.deviceNo(), event.state(), event.commandSeq(), event.eventSeq(), e.getMessage());
                try {
                    //退避：1s/2s/3s——重试期间平台可能重启/网络恢复；平台侧序守卫保证重发幂等
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    /**
     * 发送任务载体（traceId 显式随任务传递：发送线程独立于动作线程，MDC 不自动传递）
     */
    private record PendingEvent(String deviceNo, BarrierState state, Long commandSeq, String bootId,
                                long eventSeq, String traceId) {
    }
}
