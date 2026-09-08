package com.reason.barrier.reporter;

import com.reason.barrier.config.DeviceSignature;
import com.reason.barrier.config.SimProperties;
import com.reason.barrier.config.TraceIds;
import com.reason.barrier.model.BarrierState;
import com.reason.barrier.network.NetworkCondition;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
 * 不可靠链路，故障可本地制造、可进自动化剧本。</p>
 */
@Slf4j
@Component
public class HttpEventReporter implements EventReporter {

    /** 上报重试上限（协议 v2 P6：退避 1s/2s/3s，超限丢弃交平台兜底） */
    private static final int MAX_RETRY = 3;

    private final SimProperties properties;
    private final NetworkCondition network;
    private final RestTemplate restTemplate;
    private final ExecutorService sender;

    public HttpEventReporter(SimProperties properties, NetworkCondition network) {
        this.properties = properties;
        this.network = network;
        //P5：JDK HttpClient 连接复用（不再每次请求新建 TCP 连接）+ 超时参数化入 yml 可标定
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restTemplate = new RestTemplate(factory);
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
        sender.submit(() -> sendWithRetry(deviceNo, state, commandSeq, bootId, eventSeq, traceId));
    }

    private void sendWithRetry(String deviceNo, BarrierState state, Long commandSeq, String bootId,
                              long eventSeq, String traceId) {
        //发送线程独立于动作线程：MDC 不自动传递，显式置入使上报日志可被 traceId 串联
        MDC.put(TraceIds.MDC_KEY, traceId);
        try {
            doSendWithRetry(deviceNo, state, commandSeq, bootId, eventSeq, traceId);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    private void doSendWithRetry(String deviceNo, BarrierState state, Long commandSeq, String bootId,
                                long eventSeq, String traceId) {
        //网络剧本：上行方向断/单次丢包——模拟事件丢失（重试剧本：丢一次后恢复，重试应成功）
        if (network.shouldDropEvent()) {
            log.warn("[网络剧本] 事件上报被丢弃 deviceNo={} state={} seq={} eventSeq={}",
                    deviceNo, state, commandSeq, eventSeq);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //0.5 per-device HMAC：不再携共享口令——平台按 X-Device-No 查密钥验签
        headers.set("X-Device-No", deviceNo);
        //批次1 TraceId 贯穿：平台 TraceIdFilter 吃入站 X-Trace-Id 置 MDC——上报日志与平台处理日志同号
        headers.set(TraceIds.HEADER, traceId);
        String secret = properties.secretOf(deviceNo);
        if (secret == null || secret.isEmpty()) {
            log.error("[上报取消] 设备未配置密钥 deviceNo={}（联调需环境变量注入）", deviceNo);
            return;
        }
        headers.set("X-Device-Sign",
                DeviceSignature.sign(secret, DeviceSignature.canonicalEvent(deviceNo, state.getCode(), commandSeq, bootId, eventSeq)));
        //HashMap：commandSeq 可空（外力改态事件），Map.of 不允许 null
        Map<String, Object> body = new HashMap<>();
        body.put("deviceNo", deviceNo);
        body.put("state", state.getCode());
        body.put("commandSeq", commandSeq);
        body.put("bootId", bootId);
        body.put("eventSeq", eventSeq);

        int attempt = 0;
        while (true) {
            try {
                restTemplate.postForEntity(properties.getEventUrl(), new HttpEntity<>(body, headers), String.class);
                log.info("[上报成功] deviceNo={} state={} seq={} eventSeq={}", deviceNo, state, commandSeq, eventSeq);
                return;
            } catch (HttpStatusCodeException e) {
                //0.7：平台 4xx = 平台明确拒绝（签名无效 401/协议错误 400）——重试无意义，立即放弃；
                //token 漂移/密钥失配在此立即可见（不再"只看 HTTP 状态记上报成功"）
                HttpStatusCode status = e.getStatusCode();
                log.error("[上报被平台拒绝-不重试] deviceNo={} state={} seq={} http={} resp={}",
                        deviceNo, state, commandSeq, status.value(), e.getResponseBodyAsString());
                return;
            } catch (RestClientException e) {
                attempt++;
                if (attempt > MAX_RETRY) {
                    //重试耗尽：事件本次丢失（平台侧由状态查询/对账兜底——分层可靠性，不是无限重试）
                    log.error("[上报失败-重试耗尽] deviceNo={} state={} seq={} eventSeq={} cause={}",
                            deviceNo, state, commandSeq, eventSeq, e.getMessage());
                    return;
                }
                log.warn("[上报失败-将第{}次重试] deviceNo={} state={} seq={} eventSeq={} cause={}",
                        attempt, deviceNo, state, commandSeq, eventSeq, e.getMessage());
                try {
                    //退避：1s/2s/3s——重试期间平台可能重启/网络恢复；平台侧序守卫保证重发幂等
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
