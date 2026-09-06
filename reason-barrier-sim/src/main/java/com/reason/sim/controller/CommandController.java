package com.reason.sim.controller;

import com.reason.sim.model.Barrier;
import com.reason.sim.model.BarrierAction;
import com.reason.sim.registry.BarrierRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 指令接收口（平台 -> 设备）
 *
 * <p>契约（与平台 DeviceCommandServiceImpl 一致）：POST /cmd
 * body {"deviceNo": "...", "action": "OPEN|CLOSE"}；回执 code=0 表示"指令已受理"
 * （动作异步执行，状态随后主动上报平台），code!=0 表示设备拒绝（动作不合法/设备不存在）。</p>
 */
@Slf4j
@RestController
public class CommandController {

    private final BarrierRegistry registry;

    public CommandController(BarrierRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/cmd")
    public Map<String, Object> cmd(@RequestBody Map<String, String> req) {
        String deviceNo = req.get("deviceNo");
        String action = req.get("action");

        Barrier barrier = registry.get(deviceNo);
        if (barrier == null) {
            log.warn("[指令拒绝] 设备不存在 deviceNo={}", deviceNo);
            return Map.of("code", 1, "msg", "设备不存在: " + deviceNo);
        }
        try {
            barrier.execute(BarrierAction.valueOf(action));
            return Map.of("code", 0, "msg", "指令已受理");
        } catch (IllegalArgumentException e) {
            log.warn("[指令拒绝] 未知动作 deviceNo={} action={}", deviceNo, action);
            return Map.of("code", 1, "msg", "未知动作: " + action);
        } catch (IllegalStateException e) {
            //动作在当前状态不合法（已升起再升/动作中再发指令等）
            log.warn("[指令拒绝] deviceNo={} action={} reason={}", deviceNo, action, e.getMessage());
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }
}
