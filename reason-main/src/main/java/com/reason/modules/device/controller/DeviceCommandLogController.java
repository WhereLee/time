package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.device.form.DeviceCommandLogForm;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.sys.controller.AbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备指令流水管理端接口（指令闭环账本的查询侧）
 *
 * <p>流水回答审计问题：谁/什么规则/哪次重试让杆动的（triggerType），
 * 指令等到到位没有（commandStatus），重试了几次（retryCount）——
 * 与 sys_log（操作审计）互补：sys_log 记"人点了按钮"，流水记"指令的完整生命周期"。</p>
 */
@Tag(name = "指令流水")
@RestController
@RequestMapping("device/commandlog")
public class DeviceCommandLogController extends AbstractController {

    @Autowired
    private DeviceCommandLogService deviceCommandLogService;

    /**
     * 指令流水分页查询
     */
    @Operation(summary = "指令流水分页查询", description = "deviceNo/commandAction/triggerType/commandStatus 过滤；按时间倒序；权限：device:commandlog:list")
    @ApiOperationSupport(order = 1)
    @SysLog(module = "指令流水", func = "查询", value = "查询设备指令流水")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('device:commandlog:list')")
    public Result<PageUtils> list(DeviceCommandLogForm form) {
        return Result.ok(deviceCommandLogService.queryPage(form));
    }
}
