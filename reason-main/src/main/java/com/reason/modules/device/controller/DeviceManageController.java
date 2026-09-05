package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.device.form.DeviceOnlineForm;
import com.reason.modules.device.form.GateManualOpForm;
import com.reason.modules.device.service.DeviceOnlineService;
import com.reason.modules.device.service.GateManualOpService;
import com.reason.modules.sys.controller.AbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备管理端（parking/device：设备台账 + 手动抬杆闭环）
 *
 * <p>路径刻意避开 /device 前缀（设备通道保留给 X-Device-Token 鉴权的事件/心跳接入），
 * 管理端走用户体系鉴权。菜单 sys_menu 400-405（device:online:list / device:gate:lift / device:gatelog:list）。</p>
 *
 * @date 2026-09-06
 */
@Tag(name = "设备管理")
@RestController
@RequestMapping("parking/device")
public class DeviceManageController extends AbstractController {

    private final DeviceOnlineService deviceOnlineService;
    private final GateManualOpService gateManualOpService;

    public DeviceManageController(DeviceOnlineService deviceOnlineService,
                                  GateManualOpService gateManualOpService) {
        this.deviceOnlineService = deviceOnlineService;
        this.gateManualOpService = gateManualOpService;
    }

    /**
     * 设备台账分页（闸机/位检/充电桩统一在线台账）
     */
    @Operation(summary = "设备台账分页")
    @ApiOperationSupport(order = 1)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('device:online:list')")
    public Result<PageUtils> page(DeviceOnlineForm form) {
        return Result.ok(deviceOnlineService.queryPage(form));
    }

    /**
     * 手动抬杆（管理端操作：原因/车牌必录，成败均留痕）
     */
    @Operation(summary = "手动抬杆")
    @ApiOperationSupport(order = 2)
    @SysLog(module = "设备管理", func = "手动抬杆", value = "手动抬杆（异常放行）")
    @PostMapping("/lift")
    @PreAuthorize("hasAuthority('device:gate:lift')")
    public Result<Map<String, Object>> lift(@RequestBody LiftForm form) {
        boolean sent = gateManualOpService.liftGate(
                form.getDeviceNo(), form.getPlateNo(), form.getReason(), getUser());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", sent);
        data.put("msg", sent ? "抬杆指令已下发（sim 执行留痕见设备侧日志）"
                : "设备不可达或超时：留痕已记录（op_result=1），请到设备台账核对在线态");
        return Result.ok(data);
    }

    /**
     * 手动抬杆记录分页（审计留痕查询）
     */
    @Operation(summary = "抬杆记录分页")
    @ApiOperationSupport(order = 3)
    @GetMapping("/gatelog/page")
    @PreAuthorize("hasAuthority('device:gatelog:list')")
    public Result<PageUtils> gateLogPage(GateManualOpForm form) {
        return Result.ok(gateManualOpService.queryPage(form));
    }

    /** 手动抬杆请求体（操作原因审计必填；车牌人工录入可空） */
    @Data
    public static class LiftForm {
        private String deviceNo;
        private String plateNo;
        private String reason;
    }
}
