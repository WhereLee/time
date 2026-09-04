package com.reason.device.sim;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.reporter.ParkingEventReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自动剧本引擎（演示用：模拟真实停车场动态，devices 绑定车位空闲则入场，
 * 停留 staySeconds 后自动出场）。M0 默认关闭（auto.enable=false），联调用手控 /sim/event。
 */
@Slf4j
@Component
@EnableScheduling
public class SimEngine {

    private final SimDeviceRegistry registry;
    private final ParkingEventReporter reporter;
    private final DeviceSimProperties properties;

    public SimEngine(SimDeviceRegistry registry, ParkingEventReporter reporter, DeviceSimProperties properties) {
        this.registry = registry;
        this.reporter = reporter;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${reason.device-sim.auto.interval-seconds:3}000")
    public void tick() {
        if (!properties.getAuto().isEnable()) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        for (SimDevice device : registry.all()) {
            if (device.isIdle()) {
                //空闲：尝试入场（车位已被其他会话占用时业务端拒绝，debug 日志留痕即模拟真实抢占）
                try {
                    Long sessionId = reporter.reportEntry(device.getSpaceNo(), Plates.pick(now));
                    device.bindSession(sessionId, Plates.pick(now), now);
                    log.info("自动剧本：设备[{}]入场上报成功，sessionId={}", device.getDeviceNo(), sessionId);
                } catch (Exception e) {
                    log.debug("自动剧本：设备[{}]入场被拒：{}", device.getDeviceNo(), e.getMessage());
                }
            } else if (now - device.getSessionStartTs() >= properties.getAuto().getStaySeconds()) {
                //停满时长：自动出场
                try {
                    reporter.reportExit(device.getDeviceNo(), device.getSpaceNo(), device.getSessionId());
                    log.info("自动剧本：设备[{}]出场上报成功", device.getDeviceNo());
                } catch (Exception e) {
                    log.warn("自动剧本：设备[{}]出场失败：{}", device.getDeviceNo(), e.getMessage());
                } finally {
                    device.clearSession();
                }
            }
        }
    }
}
