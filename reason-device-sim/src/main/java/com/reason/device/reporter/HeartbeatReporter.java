package com.reason.device.reporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.sim.SimDeviceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备心跳批量上报（device-sim → reason-main /device/heartbeat）
 *
 * <p>A 块设备在线体系：每周期把全部设备在线态聚合上报一次（真实网关聚合语义，
 * 非设备逐台直连）；故障注入置离线的设备在此上报 online=false，main 侧台账随之离线。
 * 上报失败仅降级告警（心跳丢失由 main 侧超时判定兜底），不中断模拟。</p>
 */
@Slf4j
@Component
public class HeartbeatReporter {

    /** 失败告警抑制窗口（秒）：main 未启动等长时段失败不刷屏 */
    private static final long WARN_SUPPRESS_SECONDS = 60;

    private final DeviceSimProperties properties;
    private final SimDeviceRegistry registry;
    private final RestClient restClient;
    /** 上次告警时间（秒，volatile：调度线程单线程写，仅自身读可放宽） */
    private final AtomicLong lastWarnTs = new AtomicLong();

    public HeartbeatReporter(DeviceSimProperties properties, SimDeviceRegistry registry) {
        this.properties = properties;
        this.registry = registry;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Scheduled(fixedDelayString = "${reason.device-sim.heartbeat.interval-seconds:10}000")
    public void tick() {
        if (!properties.getHeartbeat().isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        List<Map<String, Object>> devices = new ArrayList<>(registry.all().size());
        for (SimDevice device : registry.all()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("deviceNo", device.getDeviceNo());
            d.put("online", device.isOnline());
            devices.add(d);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportedAt", now);
        body.put("devices", devices);
        try {
            String url = properties.getParkingApiBaseUrl() + "/device/heartbeat";
            JsonNode resp = restClient.post().uri(url)
                    .header("X-Device-Token", properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null || resp.path("code").asInt(0) != 0) {
                warnOnce("心跳上报被拒：" + (resp == null ? "null" : resp));
            }
        } catch (Exception e) {
            warnOnce("心跳上报失败：" + e.getMessage());
        }
    }

    /** 告警抑制：60s 窗口内只记一次 warn，其余 debug（长时段 main 不可达不刷屏） */
    private void warnOnce(String msg) {
        long now = System.currentTimeMillis() / 1000;
        long last = lastWarnTs.get();
        if (now - last >= WARN_SUPPRESS_SECONDS && lastWarnTs.compareAndSet(last, now)) {
            log.warn(msg);
        } else {
            log.debug(msg);
        }
    }
}
