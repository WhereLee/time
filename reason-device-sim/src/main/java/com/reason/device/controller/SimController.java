package com.reason.device.controller;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.reporter.ParkingEventReporter;
import com.reason.device.sim.Plates;
import com.reason.device.sim.SimDeviceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟控制台（人工联调触发入口，M0 默认手控模式）
 *
 * <p>POST /sim/event/{deviceNo}/{event}：单步触发一台设备上报（entry/exit/cancel）；
 * GET /sim/devices：查看各设备运行态。</p>
 */
@Slf4j
@RestController
@RequestMapping("/sim")
public class SimController {

    private final SimDeviceRegistry registry;
    private final ParkingEventReporter reporter;
    private final DeviceSimProperties properties;

    public SimController(SimDeviceRegistry registry, ParkingEventReporter reporter, DeviceSimProperties properties) {
        this.registry = registry;
        this.reporter = reporter;
        this.properties = properties;
    }

    /**
     * 单步触发：entry 上报后设备持有会话句柄；exit/cancel 后清除
     */
    @PostMapping("/event/{deviceNo}/{event}")
    public ResponseEntity<Map<String, Object>> event(@PathVariable("deviceNo") String deviceNo,
                                                     @PathVariable("event") String event,
                                                     @RequestParam(value = "plateNo", required = false) String plateNo,
                                                     @RequestParam(value = "reason", required = false) String reason) {
        SimDevice device = registry.findByDeviceNo(deviceNo);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        if (device == null) {
            resp.put("code", 404);
            resp.put("msg", "设备不存在：" + deviceNo);
            return ResponseEntity.badRequest().body(resp);
        }
        long now = System.currentTimeMillis() / 1000;
        try {
            switch (event.toLowerCase()) {
                case "entry" -> {
                    if (!device.isIdle()) {
                        resp.put("msg", "设备忙：已在会话中 sessionId=" + device.getSessionId());
                        return ResponseEntity.ok(resp);
                    }
                    String pn = plateNo != null ? plateNo : Plates.pick(now);
                    Long sessionId = reporter.reportEntry(device.getSpaceNo(), pn);
                    device.bindSession(sessionId, pn, now);
                    resp.put("msg", "入场上报成功");
                    resp.put("sessionId", sessionId);
                }
                case "exit" -> {
                    if (device.isIdle()) {
                        resp.put("msg", "设备空闲：无会话可出场");
                        return ResponseEntity.ok(resp);
                    }
                    Long orderId = reporter.reportExit(device.getDeviceNo(), device.getSpaceNo(), device.getSessionId());
                    device.clearSession();
                    resp.put("msg", "出场上报成功");
                    resp.put("orderId", orderId);
                }
                case "cancel" -> {
                    if (device.isIdle()) {
                        resp.put("msg", "设备空闲：无会话可取消");
                        return ResponseEntity.ok(resp);
                    }
                    String r = reason != null ? reason : "设备上报取消";
                    reporter.reportCancel(device.getDeviceNo(), device.getSessionId(), r);
                    device.clearSession();
                    resp.put("msg", "取消上报成功");
                }
                default -> {
                    resp.put("code", 400);
                    resp.put("msg", "未知事件：" + event + "（支持 entry/exit/cancel）");
                    return ResponseEntity.badRequest().body(resp);
                }
            }
        } catch (Exception e) {
            //上报失败不改变设备态：下次触发重试（模拟真实设备离线重试）
            log.error("设备[{}]上报[{}]失败：{}", deviceNo, event, e.getMessage());
            resp.put("code", 500);
            resp.put("msg", "上报失败：" + e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * 设备运行态列表
     */
    @GetMapping("/devices")
    public Map<String, Object> devices() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SimDevice device : registry.all()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("deviceNo", device.getDeviceNo());
            d.put("spaceNo", device.getSpaceNo());
            d.put("deviceType", device.getDeviceType());
            d.put("state", device.isIdle() ? "IDLE" : "OCCUPIED");
            d.put("sessionId", device.getSessionId());
            d.put("plateNo", device.getPlateNo());
            list.add(d);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("data", list);
        resp.put("autoEnabled", properties.getAuto().isEnable());
        return resp;
    }
}
