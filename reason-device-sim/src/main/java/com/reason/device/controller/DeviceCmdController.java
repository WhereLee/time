package com.reason.device.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务侧指令接收端（reason-main → device-sim /cmd/exec）
 *
 * <p>模拟执行：按 INFO 日志记录指令现场（设备执行痕迹）。
 * 寻址方式兼容两种：spaceNo（车位设备：闸杆/地锁）与 deviceNo（闸机等设备台账寻址，A 块手动抬杆）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/cmd")
public class DeviceCmdController {

    /**
     * 指令执行体（cmd：OPEN_GATE 抬杆 / LOCK 落锁 / UNLOCK 解锁）
     */
    @PostMapping("/exec")
    public Map<String, Object> exec(@RequestBody CmdBody body) {
        //设备侧执行痕迹（日志）——设备现场的对账依据；模拟语义只记录不真实动作
        log.info("设备执行控制指令：cmd={}, deviceNo={}, spaceNo={}, ts={}",
                body.getCmd(), body.getDeviceNo(), body.getSpaceNo(), body.getTs());
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("msg", "executed");
        return resp;
    }

    @Data
    public static class CmdBody {
        private String cmd;
        /** 目标设备号（闸机等设备台账寻址；与 spaceNo 二选一） */
        private String deviceNo;
        private String spaceNo;
        private Long ts;
    }
}
