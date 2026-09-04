package com.reason.modules.parking.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.Result;
import com.reason.modules.parking.service.DeviceCommandClient;
import com.reason.modules.parking.service.ParkSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备接入接口（/device/parking/*，经 DeviceAuthFilter 令牌鉴权，不经管理端 token 体系）
 *
 * <p>设备只上报业务事件（入场/出场/取消），携带自身绑定车位与车牌；
 * 会话 id 由设备在入场上报响应中持有，出场/取消时带回——设备以会话句柄闭环操作。</p>
 *
 * <p>业务全部复用 ParkSessionService（P2/P3 已验事务，本层零业务逻辑）；
 * 出场结算成功后由设备接入层触发放行指令（控制通道 fire-and-log，不参与账务事务）。</p>
 *
 * <p>设备高频上报不记操作日志（与分页查询同一标准，防 sys_log 膨胀）。</p>
 */
@Tag(name = "设备接入")
@RestController
@RequestMapping("device/parking")
public class DeviceParkingController {

    @Autowired
    private ParkSessionService parkSessionService;
    @Autowired
    private DeviceCommandClient deviceCommandClient;

    /**
     * 入场上报：车停入 → 创建进行中会话
     */
    @Operation(summary = "设备入场上报")
    @ApiOperationSupport(order = 1)
    @PostMapping("/entry")
    public Result<Long> entry(@RequestBody DeviceEventBody body) {
        Long sessionId = parkSessionService.entry(body.getSpaceNo(), body.getPlateNo());
        return Result.ok(sessionId);
    }

    /**
     * 出场上报：结算完成 → 下发开闸放行指令（设备通道 fire-and-log）
     */
    @Operation(summary = "设备出场上报")
    @ApiOperationSupport(order = 2)
    @PostMapping("/exit")
    public Result<Map<String, Object>> exit(@RequestBody DeviceEventBody body) {
        Long orderId = parkSessionService.exit(body.getSessionId());
        //放行指令：失败仅告警（账务已完成，指令可重试性归 M2 设备治理）
        deviceCommandClient.sendCommand(DeviceCommandClient.CMD_OPEN_GATE, body.getSpaceNo());
        return Result.ok(Map.of("orderId", orderId));
    }

    /**
     * 取消上报：设备侧撤销（如误触发/车辆未停留离开）
     */
    @Operation(summary = "设备取消上报")
    @ApiOperationSupport(order = 3)
    @PostMapping("/cancel")
    public Result cancel(@RequestBody DeviceEventBody body) {
        parkSessionService.cancel(body.getSessionId(), body.getCancelReason());
        return Result.ok();
    }

    /**
     * 设备事件上报体（deviceNo 保留字段：设备注册台账 M2 建立前不做绑定校验）
     */
    @Data
    public static class DeviceEventBody {
        private String deviceNo;
        private String spaceNo;
        private String plateNo;
        private Long sessionId;
        private String cancelReason;
    }
}
