package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.device.form.DeviceAlarmForm;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.sys.controller.AbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备告警管理端接口（"故障态+告警"机制的查询侧）
 *
 * <p>告警由系统自动触发（监控任务/事件处理/心跳对账），管理端只读——
 * 处置动作（复位/检修）发生在物理世界，处置结果由设备重新上线/状态恢复体现。</p>
 */
@Tag(name = "设备告警")
@RestController
@RequestMapping("device/alarm")
public class DeviceAlarmController extends AbstractController {

    @Autowired
    private DeviceAlarmService deviceAlarmService;

    /**
     * 告警分页查询
     */
    @Operation(summary = "告警分页查询", description = "deviceNo/alarmType/alarmHandled 精确过滤；按时间倒序；alarmHandled 缺省只看未确认；权限：device:alarm:list")
    @ApiOperationSupport(order = 1)
    @SysLog(module = "设备告警", func = "查询", value = "查询设备告警列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('device:alarm:list')")
    public Result<PageUtils> list(DeviceAlarmForm form) {
        return Result.ok(deviceAlarmService.queryPage(form));
    }

    /**
     * 告警确认处理（0.4 告警闭环：处置+关闭，记录处理人/时间——应急通道不疲劳）
     */
    @Operation(summary = "告警确认处理", description = "确认告警已处置：0->1 并记录处理人与时间；重复确认/不存在的告警拒绝；权限：device:alarm:handle")
    @ApiOperationSupport(order = 2)
    @SysLog(module = "设备告警", func = "处理", value = "确认处理设备告警")
    @PostMapping("/handle")
    @PreAuthorize("hasAuthority('device:alarm:handle')")
    public Result<String> handle(@RequestBody Map<String, Long> body) {
        Long alarmId = body.get("alarmId");
        if (alarmId == null) {
            throw new com.reason.common.exception.RRException("告警ID不能为空");
        }
        deviceAlarmService.handle(alarmId, getUserId());
        return Result.ok();
    }
}
