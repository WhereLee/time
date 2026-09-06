package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.Result;
import com.reason.modules.device.form.DeviceCommandForm;
import com.reason.modules.device.service.DeviceCommandService;
import com.reason.modules.sys.controller.AbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备指令管理端接口（块3：远程升/降杆）
 *
 * <p>管理端只负责"发出指令"，动作合法性由设备侧裁决，状态由设备事件回报更新——
 * 本接口调用成功仅代表"指令已受理"，不代表"杆已升起"。</p>
 */
@Tag(name = "设备指令")
@RestController
@RequestMapping("device/command")
public class DeviceCommandController extends AbstractController {

    @Autowired
    private DeviceCommandService deviceCommandService;

    /**
     * 升杆指令
     */
    @Operation(summary = "升起", description = "向设备下发升起指令；返回仅代表指令已受理，状态以设备事件回报为准；权限：device:command:open")
    @ApiOperationSupport(order = 1)
    @SysLog(module = "设备台账", func = "升起", value = "远程升杆指令")
    @PostMapping("/open")
    @PreAuthorize("hasAuthority('device:command:open')")
    public Result<String> open(@RequestBody DeviceCommandForm form) {
        deviceCommandService.open(form.getDeviceNo());
        return Result.ok();
    }

    /**
     * 降杆指令
     */
    @Operation(summary = "降下", description = "向设备下发降下指令；返回仅代表指令已受理，状态以设备事件回报为准；权限：device:command:close")
    @ApiOperationSupport(order = 2)
    @SysLog(module = "设备台账", func = "降下", value = "远程降杆指令")
    @PostMapping("/close")
    @PreAuthorize("hasAuthority('device:command:close')")
    public Result<String> close(@RequestBody DeviceCommandForm form) {
        deviceCommandService.close(form.getDeviceNo());
        return Result.ok();
    }
}
