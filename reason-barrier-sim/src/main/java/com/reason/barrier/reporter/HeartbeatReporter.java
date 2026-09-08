package com.reason.barrier.reporter;

import com.reason.barrier.config.DeviceSignature;
import com.reason.barrier.config.SimProperties;
import com.reason.barrier.config.TraceIds;
import com.reason.barrier.model.Barrier;
import com.reason.barrier.network.NetworkCondition;
import com.reason.barrier.registry.BarrierRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 心跳上报器（设备侧的"我还活着 + 我现在是什么状态"）
 *
 * <p>两个时机：
 * <ul>
 *   <li>上电自述：启动立即上报一轮——断电恢复边界的关键：设备重启回到初始态（DOWN），
 *       但平台台账可能还记着断电前的 UP；自述让平台第一时间校正快照，而不是等下一次指令才发现；</li>
 *   <li>周期心跳：每 interval 秒上报一轮——平台侧 Redis key TTL 续命，
 *       停止心跳（断电/断网/进程挂）后 TTL 自然过期即判离线。</li>
 * </ul>
 * 心跳失败只记日志不重试（下一次心跳就是天然的重试——周期上报的自愈性；
 * 持续失败会体现为平台侧离线告警，可见性由平台兜底）。</p>
 *
 * <p>P5 并行化（T11）：单线程逐台串行上报 = 一台慢（网络延迟/挂起）拖垮整组节拍、
 * scheduleAtFixedRate 直接跳轮、TTL 判活失真。改为<strong>每台设备独立调度任务</strong>
 * （共享有界线程池并行执行）：谁慢只丢自己的轮，其余设备节拍不受影响；HTTP 客户端
 * 走 JDK HttpClient 连接复用（每请求不再新建 TCP 连接）+ 超时参数化入 yml 可标定。</p>
 */
@Slf4j
@Component
public class HeartbeatReporter {

    private final SimProperties properties;
    private final BarrierRegistry registry;
    private final NetworkCondition network;
    private final RestTemplate restTemplate;
    private final ScheduledExecutorService scheduler;

    public HeartbeatReporter(SimProperties properties, BarrierRegistry registry, NetworkCondition network) {
        this.properties = properties;
        this.registry = registry;
        this.network = network;
        //心跳通道独立超时（P5 参数化：connect/read 秒数可配，按注入器剧本标定）
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        this.restTemplate = new RestTemplate(factory);
        AtomicInteger seq = new AtomicInteger();
        //P5：有界并行（上限 heartbeatThreads，按需创建不预占）；每设备独立周期任务，单台阻塞不拖累他人
        this.scheduler = Executors.newScheduledThreadPool(properties.getHeartbeatThreads(), r -> {
            Thread t = new Thread(r, "barrier-heartbeat-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void start() {
        //上电自述（断电恢复：重启后立即让平台校正台账，不等下一次指令/心跳周期）——并行一轮
        for (Barrier barrier : registry.all()) {
            scheduler.submit(() -> report(barrier, "上电自述"));
        }
        //周期心跳：每台设备独立节拍（P5——串行时代一台慢会让整组跳轮，TTL 30s 判活失真）
        int interval = properties.getHeartbeatIntervalSeconds();
        for (Barrier barrier : registry.all()) {
            scheduler.scheduleAtFixedRate(() -> report(barrier, "周期心跳"), interval, interval, TimeUnit.SECONDS);
        }
        log.info("心跳上报已启动：间隔 {}s，设备 {} 台（并行线程上限 {}） -> {}", interval,
                registry.size(), properties.getHeartbeatThreads(), properties.getHeartbeatUrl());
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    private void report(Barrier barrier, String scene) {
        String deviceNo = barrier.getDeviceNo();
        //批次1 TraceId：心跳是设备自发周期信号（无上游链路），每轮每设备一个 traceId——
        //使"某台设备某一轮心跳"在两侧日志可精确定位（报文级排障）
        String traceId = TraceIds.generate();
        MDC.put(TraceIds.MDC_KEY, traceId);
        try {
            doReport(barrier, scene, deviceNo, traceId);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    private void doReport(Barrier barrier, String scene, String deviceNo, String traceId) {
        //网络剧本：上行方向断（T20 单向断：事件+心跳都上不去）——心跳跟随方向断，不消费单次丢包
        if (network.shouldDropHeartbeat()) {
            log.warn("[网络剧本] 心跳被丢弃(上行阻断) deviceNo={}", deviceNo);
            return;
        }
        //网络剧本：人为延迟（P5/T11）——sleep 发生在设备自己的任务线程里，只拖慢本设备轮次
        long delayMillis = network.getHeartbeatDelayMillis();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            log.warn("[网络剧本] 心跳被延迟 {}ms deviceNo={}", delayMillis, deviceNo);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            //0.5 per-device HMAC（心跳签名：deviceNo|state）——共享口令已退役
            headers.set("X-Device-No", deviceNo);
            //批次1 TraceId 贯穿：平台 TraceIdFilter 吃入站头置 MDC
            headers.set(TraceIds.HEADER, traceId);
            String secret = properties.secretOf(deviceNo);
            if (secret == null || secret.isEmpty()) {
                log.error("[{}] {}心跳取消：设备未配置密钥（联调需环境变量注入）", deviceNo, scene);
                return;
            }
            headers.set("X-Device-Sign", DeviceSignature.sign(secret,
                    DeviceSignature.canonicalHeartbeat(deviceNo, barrier.getState().getCode())));
            Map<String, Object> body = Map.of(
                    "deviceNo", deviceNo,
                    "state", barrier.getState().getCode());
            restTemplate.postForObject(properties.getHeartbeatUrl(),
                    new HttpEntity<>(body, headers), Map.class);
            log.debug("[{}] {}上报 state={}", deviceNo, scene, barrier.getState());
        } catch (Exception e) {
            //心跳失败不重试：下一轮周期就是天然重试；持续失败由平台离线告警兜底可见；
            //平台 4xx（密钥失配）在 error 日志立即可见（0.7：不再"只看 HTTP 状态记成功"）
            log.warn("[{}] {}上报失败 cause={}", deviceNo, scene, e.getMessage());
        }
    }
}
