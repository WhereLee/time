package com.reason.modules.device.controller;

import com.reason.common.utils.Result;
import com.reason.modules.device.service.DeviceOnlineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设备心跳接入接口：/device/heartbeat（X-Device-Token 鉴权，DeviceAuthFilter 覆盖 /device/*）
 *
 * <p>网关聚合上报协议：一次携带全部设备在线快照（sim 每 10s 一批）；
 * 与业务事件通道（/device/parking/*、/device/charging/*）分离——心跳只维护在线台账，
 * 不承载业务。</p>
 */
@Tag(name = "设备心跳")
@RestController
@RequestMapping("/device")
public class DeviceHeartbeatController {

    private final DeviceOnlineService deviceOnlineService;

    public DeviceHeartbeatController(DeviceOnlineService deviceOnlineService) {
        this.deviceOnlineService = deviceOnlineService;
    }

    @Operation(summary = "心跳批量上报（网关聚合）")
    @PostMapping("/heartbeat")
    public Result<Object> heartbeat(@RequestBody HeartbeatRequest request) {
        deviceOnlineService.recordHeartbeat(request.getReportedAt(), request.getDevices());
        return Result.ok();
    }

    /** 心跳批次请求体（与设备通道协议一致） */
    @Data
    public static class HeartbeatRequest {
        /** 批次时刻（秒） */
        private Long reportedAt;
        /** 设备在线快照 */
        private List<Map<String, Object>> devices = new ArrayList<>();
    }
}
