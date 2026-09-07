package com.reason.barrier.controller;

import com.reason.barrier.model.Barrier;
import com.reason.barrier.registry.BarrierRegistry;
import com.reason.barrier.service.DeviceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟器操控台（扮演"物理世界的意外"——不是平台功能，是设备侧的演示注入口）
 *
 * <p>真实世界里这些事不由平台发起：杆被车撞了（tamper）、机械卡死了（fault）、
 * 检修工复位了（recover）、杆下停了一辆车（vehicle）。模拟器提供注入口，
 * 让"十公里外不可靠"的每个边界都能在本地被复现和验证。</p>
 *
 * <p>契约：POST /sim/{fault|recover|tamper|vehicle} body {"deviceNo": "...", ...}；
 * GET /sim/status 返回全部杆的实时快照（演示/联调观察用）。</p>
 */
@RestController
@RequestMapping("/sim")
public class SimController {

    private final DeviceService deviceService;
    private final BarrierRegistry registry;

    public SimController(DeviceService deviceService, BarrierRegistry registry) {
        this.deviceService = deviceService;
        this.registry = registry;
    }

    /** 卡杆故障注入：下一个动作执行到一半卡死转 FAULT */
    @PostMapping("/fault")
    public Map<String, Object> fault(@RequestBody Map<String, String> req) {
        return invoke(req.get("deviceNo"), deviceService::injectFault);
    }

    /** 人工复位：清除故障，杆停在 FAULT 时回落 DOWN 并上报 */
    @PostMapping("/recover")
    public Map<String, Object> recover(@RequestBody Map<String, String> req) {
        return invoke(req.get("deviceNo"), deviceService::recover);
    }

    /** 外力改态：物理世界把杆掰到 UP/DOWN（不经指令），设备主动上报 */
    @PostMapping("/tamper")
    public Map<String, Object> tamper(@RequestBody Map<String, String> req) {
        String deviceNo = req.get("deviceNo");
        String state = req.get("state");
        try {
            return Map.of("code", 0, "msg", deviceService.tamper(deviceNo, state));
        } catch (IllegalArgumentException e) {
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }

    /** 防砸信号：杆下探测器检测到车进出（true=有车，CLOSE 将被互锁拒绝） */
    @PostMapping("/vehicle")
    public Map<String, Object> vehicle(@RequestBody Map<String, Object> req) {
        String deviceNo = (String) req.get("deviceNo");
        boolean present = Boolean.parseBoolean(String.valueOf(req.get("present")));
        try {
            return Map.of("code", 0, "msg", deviceService.setVehicle(deviceNo, present));
        } catch (IllegalArgumentException e) {
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }

    /** 全部杆的实时快照（状态/故障注入/防砸信号——物理世界的真相在这，平台台账只是快照） */
    @GetMapping("/status")
    public Map<String, Object> status() {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Barrier barrier : registry.all()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceNo", barrier.getDeviceNo());
            item.put("name", barrier.getName());
            item.put("state", barrier.getState().name());
            item.put("stateCode", barrier.getState().getCode());
            item.put("faultInjected", barrier.isFaultInjected());
            item.put("vehiclePresent", barrier.isVehiclePresent());
            devices.add(item);
        }
        return Map.of("code", 0, "devices", devices);
    }

    private Map<String, Object> invoke(String deviceNo, java.util.function.Function<String, String> op) {
        try {
            return Map.of("code", 0, "msg", op.apply(deviceNo));
        } catch (IllegalArgumentException e) {
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }
}
