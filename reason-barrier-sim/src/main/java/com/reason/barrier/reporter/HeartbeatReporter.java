package com.reason.barrier.reporter;

import com.reason.barrier.config.DeviceSignature;
import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.Barrier;
import com.reason.barrier.network.NetworkCondition;
import com.reason.barrier.registry.BarrierRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        //心跳通道独立超时：心跳是轻请求，快速失败不积压（挤占下一轮周期）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "barrier-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void start() {
        //上电自述（断电恢复：重启后立即让平台校正台账，不等下一次指令/心跳周期）
        reportAll("上电自述");
        int interval = properties.getHeartbeatIntervalSeconds();
        scheduler.scheduleAtFixedRate(() -> reportAll("周期心跳"), interval, interval, TimeUnit.SECONDS);
        log.info("心跳上报已启动：间隔 {}s，设备 {} 台 -> {}", interval, registry.size(), properties.getHeartbeatUrl());
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    private void reportAll(String scene) {
        for (Barrier barrier : registry.all()) {
            //网络剧本：上行方向断（T20 单向断：事件+心跳都上不去）——心跳跟随方向断，不消费单次丢包
            if (network.shouldDropHeartbeat()) {
                log.warn("[网络剧本] 心跳被丢弃(上行阻断) deviceNo={}", barrier.getDeviceNo());
                continue;
            }
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                //0.5 per-device HMAC（心跳签名：deviceNo|state）——共享口令已退役
                headers.set("X-Device-No", barrier.getDeviceNo());
                String secret = properties.secretOf(barrier.getDeviceNo());
                if (secret == null || secret.isEmpty()) {
                    log.error("[{}] {}心跳取消：设备未配置密钥（联调需环境变量注入）", barrier.getDeviceNo(), scene);
                    continue;
                }
                headers.set("X-Device-Sign", DeviceSignature.sign(secret,
                        DeviceSignature.canonicalHeartbeat(barrier.getDeviceNo(), barrier.getState().getCode())));
                Map<String, Object> body = Map.of(
                        "deviceNo", barrier.getDeviceNo(),
                        "state", barrier.getState().getCode());
                restTemplate.postForObject(properties.getHeartbeatUrl(),
                        new HttpEntity<>(body, headers), Map.class);
                log.debug("[{}] {}上报 state={}", barrier.getDeviceNo(), scene, barrier.getState());
            } catch (Exception e) {
                //心跳失败不重试：下一轮周期就是天然重试；持续失败由平台离线告警兜底可见；
                //平台 4xx（密钥失配）在 error 日志立即可见（0.7：不再"只看 HTTP 状态记成功"）
                log.warn("[{}] {}上报失败 cause={}", barrier.getDeviceNo(), scene, e.getMessage());
            }
        }
    }
}
