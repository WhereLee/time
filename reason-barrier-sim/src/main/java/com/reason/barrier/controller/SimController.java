package com.reason.barrier.controller;

import com.reason.barrier.config.TraceIds;
import com.reason.barrier.model.Barrier;
import com.reason.barrier.network.NetworkCondition;
import com.reason.barrier.registry.BarrierRegistry;
import com.reason.barrier.service.DeviceService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
    private final NetworkCondition network;

    public SimController(DeviceService deviceService, BarrierRegistry registry, NetworkCondition network) {
        this.deviceService = deviceService;
        this.registry = registry;
        this.network = network;
    }

    /** 卡杆故障注入：下一个动作执行到一半卡死转 FAULT */
    @PostMapping("/fault")
    public Map<String, Object> fault(@RequestBody Map<String, String> req) {
        return withTrace(() -> invoke(req.get("deviceNo"), deviceService::injectFault));
    }

    /** 人工复位：清除故障，杆停在 FAULT 时回落 DOWN 并上报 */
    @PostMapping("/recover")
    public Map<String, Object> recover(@RequestBody Map<String, String> req) {
        return withTrace(() -> invoke(req.get("deviceNo"), deviceService::recover));
    }

    /** 外力改态：物理世界把杆掰到 UP/DOWN（不经指令），设备主动上报 */
    @PostMapping("/tamper")
    public Map<String, Object> tamper(@RequestBody Map<String, String> req) {
        String deviceNo = req.get("deviceNo");
        String state = req.get("state");
        return withTrace(() -> {
            try {
                return Map.of("code", 0, "msg", deviceService.tamper(deviceNo, state));
            } catch (IllegalArgumentException e) {
                return Map.of("code", 1, "msg", e.getMessage());
            }
        });
    }

    /** 防砸信号：杆下探测器检测到车进出（true=有车，CLOSE 将被互锁拒绝） */
    @PostMapping("/vehicle")
    public Map<String, Object> vehicle(@RequestBody Map<String, Object> req) {
        String deviceNo = (String) req.get("deviceNo");
        boolean present = Boolean.parseBoolean(String.valueOf(req.get("present")));
        return withTrace(() -> {
            try {
                return Map.of("code", 0, "msg", deviceService.setVehicle(deviceNo, present));
            } catch (IllegalArgumentException e) {
                return Map.of("code", 1, "msg", e.getMessage());
            }
        });
    }

    /** 静默故障注入（0.3 卡滞不终态：受理但不动作不上报——验证平台自动校正熔断） */
    @PostMapping("/stuck")
    public Map<String, Object> stuck(@RequestBody Map<String, Object> req) {
        String deviceNo = (String) req.get("deviceNo");
        boolean on = Boolean.parseBoolean(String.valueOf(req.get("on")));
        return withTrace(() -> {
            try {
                return Map.of("code", 0, "msg", deviceService.setStuck(deviceNo, on));
            } catch (IllegalArgumentException e) {
                return Map.of("code", 1, "msg", e.getMessage());
            }
        });
    }

    /** 卡动作中注入（0.4：动作到 MOVING 后永不终态——验证平台 MOVING 巡检告警） */
    @PostMapping("/stuck-moving")
    public Map<String, Object> stuckMoving(@RequestBody Map<String, Object> req) {
        String deviceNo = (String) req.get("deviceNo");
        boolean on = Boolean.parseBoolean(String.valueOf(req.get("on")));
        return withTrace(() -> {
            try {
                return Map.of("code", 0, "msg", deviceService.setStuckMoving(deviceNo, on));
            } catch (IllegalArgumentException e) {
                return Map.of("code", 1, "msg", e.getMessage());
            }
        });
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
            item.put("bootId", barrier.getBootId());
            item.put("eventSeq", barrier.getEventSeq());
            item.put("lastCommandSeq", barrier.getLastSeq());
            devices.add(item);
        }
        return Map.of("code", 0, "devices", devices);
    }

    /**
     * 网络剧本注入（应用层注入器——逻辑形态：设备链路不可信，故障可本地制造、可进自动化剧本）
     *
     * <p>body：{blockUpstream, blockDownstream, dropNextEvent, heartbeatDelayMillis}（缺省关）。
     * blockUpstream=上行断（事件+心跳都上不去——T20 单向断剧本）；
     * blockDownstream=下行断（入站指令/查询被拒——反向单向断剧本）；
     * dropNextEvent=丢下一次事件上报（验证上报失败退避重试）；
     * heartbeatDelayMillis=心跳人为延迟毫秒（P5/T11：慢设备不拖垮整组节拍的验证剧本）。</p>
     */
    @PostMapping("/network")
    public Map<String, Object> network(@RequestBody Map<String, Object> req) {
        boolean up = Boolean.parseBoolean(String.valueOf(req.get("blockUpstream")));
        boolean events = Boolean.parseBoolean(String.valueOf(req.get("blockEvents")));
        boolean down = Boolean.parseBoolean(String.valueOf(req.get("blockDownstream")));
        boolean drop = Boolean.parseBoolean(String.valueOf(req.get("dropNextEvent")));
        long hbDelay = req.get("heartbeatDelayMillis") == null ? 0
                : Long.parseLong(String.valueOf(req.get("heartbeatDelayMillis")));
        network.setBlockUpstream(up);
        network.setBlockEvents(events);
        network.setBlockDownstream(down);
        network.setDropNextEvent(drop);
        network.setHeartbeatDelayMillis(hbDelay);
        return Map.of("code", 0,
                "msg", String.format("网络剧本已设：上行阻断=%s 事件独立断=%s 下行阻断=%s 丢下一次事件=%s 心跳延迟=%sms",
                        up, events, down, drop, hbDelay));
    }

    private Map<String, Object> invoke(String deviceNo, java.util.function.Function<String, String> op) {
        try {
            return Map.of("code", 0, "msg", op.apply(deviceNo));
        } catch (IllegalArgumentException e) {
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }

    /**
     * 注入口统一链路包装（批次1）：每次注入一个 traceId 置 MDC——
     * 使"注入口请求 → 设备动作 → 事件上报"在 sim 日志里同号可 grep（剧本取证依赖此）
     */
    private Map<String, Object> withTrace(Supplier<Map<String, Object>> action) {
        MDC.put(TraceIds.MDC_KEY, TraceIds.generate());
        try {
            return action.get();
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}
