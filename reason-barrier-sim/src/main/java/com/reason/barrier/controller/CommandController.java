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
 * body {"deviceNo": "...", "action": "OPEN|CLOSE", "commandSeq": 123}；
 * 回执 code=0 = 指令已受理（含幂等忽略——设备保证不重复动作），
 * code=1 = 设备拒绝（动作不合法/防砸互锁/故障态/设备不存在/缺 seq），msg=原因。</p>
 */
@RestController
public class CommandController {

    private final DeviceService deviceService;

    public CommandController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/cmd")
    public Map<String, Object> cmd(@RequestBody Map<String, Object> req) {
        String deviceNo = (String) req.get("deviceNo");
        String action = (String) req.get("action");
        Object seqObj = req.get("commandSeq");
        //seq 是幂等契约的必要字段：缺失直接拒绝（无 seq 指令无法去重，宁可拒收）
        if (seqObj == null) {
            return Map.of("code", 1, "msg", "缺少指令序号 commandSeq");
        }
        long seq;
        try {
            seq = Long.parseLong(String.valueOf(seqObj));
        } catch (NumberFormatException e) {
            return Map.of("code", 1, "msg", "commandSeq 非法: " + seqObj);
        }

        try {
            String msg;
            if ("OPEN".equals(action)) {
                msg = deviceService.open(deviceNo, seq);
            } else if ("CLOSE".equals(action)) {
                msg = deviceService.close(deviceNo, seq);
            } else {
                return Map.of("code", 1, "msg", "未知动作: " + action);
            }
            return Map.of("code", 0, "msg", msg);
        } catch (IllegalStateException e) {
            //设备拒绝动作（状态机裁决/防砸互锁/故障态）——显式回执，平台记 SEND_FAILED
            return Map.of("code", 1, "msg", e.getMessage());
        } catch (IllegalArgumentException e) {
            //设备不存在：平台向未安装的设备发指令
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }
}
