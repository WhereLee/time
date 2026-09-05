package com.reason.modules.charging.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.utils.Result;
import com.reason.modules.charging.service.ChargeSessionService;
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
 * 充电设备接入接口（/device/charging/*，经 DeviceAuthFilter 令牌鉴权，不经管理端 token 体系）
 *
 * <p>桩只上报业务事件（开始/结束/取消），携带自身桩编号与车牌；会话 id 由桩在开始上报响应中持有，
 * 结束/取消时带回——与停车设备（DeviceParkingController）同一会话句柄模式。</p>
 *
 * <p>充电无物理放行指令（无闸/锁语义），结束结算后无控制通道调用；业务全部复用
 * ChargeSessionService（M1-3/M1-4 已验事务），本层零业务逻辑。设备高频上报不记操作日志。</p>
 *
 * @date 2026-09-05
 */
@Tag(name = "充电设备接入")
@RestController
@RequestMapping("device/charging")
public class DeviceChargingController {

    @Autowired
    private ChargeSessionService chargeSessionService;

    /**
     * 充电开始上报：桩编号 + 车牌 → 创建充电会话（服务端锚定该车位进行中停车会话并校验车牌一致）
     */
    @Operation(summary = "设备充电开始上报")
    @ApiOperationSupport(order = 1)
    @PostMapping("/start")
    public Result<Long> start(@RequestBody ChargingEventBody body) {
        Long sessionId = chargeSessionService.start(body.getPileNo(), body.getPlateNo());
        return Result.ok(sessionId);
    }

    /**
     * 充电结束上报：会话 id + 总电量（Wh）→ 结算订单（电量>0 签发免停权益）
     */
    @Operation(summary = "设备充电结束上报")
    @ApiOperationSupport(order = 2)
    @PostMapping("/finish")
    public Result<Map<String, Object>> finish(@RequestBody ChargingEventBody body) {
        Long orderId = chargeSessionService.finish(body.getSessionId(), body.getEnergyWh());
        return Result.ok(Map.of("orderId", orderId));
    }

    /**
     * 充电取消上报：会话 id + 原因（如设备侧中止）
     */
    @Operation(summary = "设备充电取消上报")
    @ApiOperationSupport(order = 3)
    @PostMapping("/cancel")
    public Result cancel(@RequestBody ChargingEventBody body) {
        chargeSessionService.cancel(body.getSessionId(), body.getCancelReason());
        return Result.ok();
    }

    /**
     * 充电事件上报体（deviceNo 保留字段：设备注册台账 M2 建立前不做绑定校验）
     */
    @Data
    public static class ChargingEventBody {
        private String deviceNo;
        private String pileNo;
        private String plateNo;
        private Long sessionId;
        private Long energyWh;
        private String cancelReason;
    }
}
