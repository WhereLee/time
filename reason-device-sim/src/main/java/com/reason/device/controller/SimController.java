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
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟控制台（人工联调触发入口，M0 默认手控模式）
 *
 * <p>POST /sim/event/{deviceNo}/{event}：单步触发一台设备上报——停车设备 entry/exit/cancel；
 * 充电桩 start/finish/cancel（finish 可带 energyWh，缺省随机 10~60 kWh 模拟计量）；
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
                                                     @RequestParam(value = "reason", required = false) String reason,
                                                     @RequestParam(value = "energyWh", required = false) Long energyWh) {
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
                    if (device.getSpaceNo() == null) {
                        resp.put("code", 400);
                        resp.put("msg", "设备无绑定车位，不支持 entry（闸机/位检入场语义 B 块接入）");
                        return ResponseEntity.badRequest().body(resp);
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
                    String r = reason != null ? reason : (device.isCharger() ? "设备上报取消充电" : "设备上报取消");
                    if (device.isCharger()) {
                        reporter.reportChargeCancel(device.getDeviceNo(), device.getSessionId(), r);
                    } else {
                        reporter.reportCancel(device.getDeviceNo(), device.getSessionId(), r);
                    }
                    device.clearSession();
                    resp.put("msg", "取消上报成功");
                }
                case "start" -> {
                    //充电桩专属：充电开始（桩编号即 deviceNo，须先有停车会话由服务端锚定）
                    if (!device.isCharger()) {
                        resp.put("code", 400);
                        resp.put("msg", "非充电桩设备不支持 start 事件");
                        return ResponseEntity.badRequest().body(resp);
                    }
                    if (!device.isIdle()) {
                        resp.put("msg", "设备忙：已在会话中 sessionId=" + device.getSessionId());
                        return ResponseEntity.ok(resp);
                    }
                    String pn = plateNo != null ? plateNo : Plates.pick(now);
                    Long sessionId = reporter.reportChargeStart(device.getDeviceNo(), pn);
                    device.bindSession(sessionId, pn, now);
                    resp.put("msg", "充电开始上报成功");
                    resp.put("sessionId", sessionId);
                }
                case "finish" -> {
                    //充电桩专属：充电结束（缺省电量随机 10~60 kWh 模拟真实计量上报）
                    if (!device.isCharger()) {
                        resp.put("code", 400);
                        resp.put("msg", "非充电桩设备不支持 finish 事件");
                        return ResponseEntity.badRequest().body(resp);
                    }
                    if (device.isIdle()) {
                        resp.put("msg", "设备空闲：无充电会话可结束");
                        return ResponseEntity.ok(resp);
                    }
                    long wh = energyWh != null ? energyWh
                            : ThreadLocalRandom.current().nextLong(10_000L, 60_001L);
                    Long orderId = reporter.reportChargeFinish(device.getDeviceNo(), device.getSessionId(), wh);
                    device.clearSession();
                    resp.put("msg", "充电结束上报成功");
                    resp.put("orderId", orderId);
                    resp.put("energyWh", wh);
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
            d.put("deviceType", device.getDeviceType() == null ? null : device.getDeviceType().name());
            d.put("online", device.isOnline());
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

    /**
     * 故障注入：设备置离线（心跳上报 online=false，main 台账随之离线）
     */
    @PostMapping("/device/offline/{deviceNo}")
    public ResponseEntity<Map<String, Object>> offline(@PathVariable("deviceNo") String deviceNo) {
        return setOnline(deviceNo, false);
    }

    /**
     * 故障恢复：设备置回在线
     */
    @PostMapping("/device/online/{deviceNo}")
    public ResponseEntity<Map<String, Object>> online(@PathVariable("deviceNo") String deviceNo) {
        return setOnline(deviceNo, true);
    }

    private ResponseEntity<Map<String, Object>> setOnline(String deviceNo, boolean online) {
        SimDevice device = registry.findByDeviceNo(deviceNo);
        Map<String, Object> resp = new LinkedHashMap<>();
        if (device == null) {
            resp.put("code", 404);
            resp.put("msg", "设备不存在：" + deviceNo);
            return ResponseEntity.badRequest().body(resp);
        }
        device.setOnline(online);
        resp.put("code", 0);
        resp.put("msg", (online ? "恢复在线" : "置为离线") + "：" + deviceNo);
        return ResponseEntity.ok(resp);
    }
}
