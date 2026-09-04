package com.reason.device.controller;

import com.reason.device.config.DeviceSimProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务控制指令接收端（reason-main → device-sim /cmd/exec）
 *
 * <p>模拟执行：打 INFO 日志记录指令现场（设备侧执行痕迹）。
 * M0 无指令重试/乱序处理——设备侧幂等执行语义属 M2 设备治理。</p>
 */
@Slf4j
@RestController
@RequestMapping("/cmd")
public class DeviceCmdController {

    /**
     * 指令执行体（cmd：OPEN_GATE 开闸放行 / LOCK 落锁 / UNLOCK 解锁）
     */
    @PostMapping("/exec")
    public Map<String, Object> exec(@RequestBody CmdBody body) {
        //设备侧执行痕迹：日志即“设备真的动了”（模拟进程无物理动作）
        log.info("设备执行控制指令：cmd={}, spaceNo={}, ts={}", body.getCmd(), body.getSpaceNo(), body.getTs());
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("msg", "executed");
        return resp;
    }

    @Data
    public static class CmdBody {
        private String cmd;
        private String spaceNo;
        private Long ts;
    }
}
