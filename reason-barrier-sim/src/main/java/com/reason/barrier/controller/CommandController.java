package com.reason.barrier.controller;

import com.reason.barrier.service.DeviceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 指令接收口（平台 -> 设备）——只做 HTTP 契约翻译，业务在 DeviceService
 *
 * <p>契约（与平台 DeviceCommandServiceImpl 一致）：POST /cmd
 * body {"deviceNo": "...", "action": "OPEN|CLOSE"}；回执 code=0 表示"指令已受理"
 * （动作异步执行，状态随后主动上报平台），code!=0 表示设备拒绝（动作不合法/设备不存在）。</p>
 */
@RestController
public class CommandController {

    private final DeviceService deviceService;

    public CommandController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/cmd")
    public Map<String, Object> cmd(@RequestBody Map<String, String> req) {
        String deviceNo = req.get("deviceNo");
        String action = req.get("action");
        try {
            String msg;
            if ("OPEN".equals(action)) {
                msg = deviceService.open(deviceNo);
            } else if ("CLOSE".equals(action)) {
                msg = deviceService.close(deviceNo);
            } else {
                return Map.of("code", 1, "msg", "未知动作: " + action);
            }
            return Map.of("code", 0, "msg", msg);
        } catch (IllegalArgumentException e) {
            //设备不存在：平台向未安装的设备发指令
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }
}
