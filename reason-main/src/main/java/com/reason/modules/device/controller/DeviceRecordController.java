package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.device.form.DeviceRecordForm;
import com.reason.modules.device.service.DeviceRecordService;
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

/**
 * 设备台账管理端接口（块1：登记 + 分页查询）
 *
 * <p>管理端只操作"档案"；对设备的远程动作（升/降指令）是后续块，走设备通道，
 * 不在此 Controller 出现——接口职责与对象职责一一对应。</p>
 */
@Tag(name = "设备台账")
@RestController
@RequestMapping("device/record")
public class DeviceRecordController extends AbstractController {

    @Autowired
    private DeviceRecordService deviceRecordService;

    /**
     * 台账分页查询
     */
    @Operation(summary = "台账分页查询", description = "设备档案列表；deviceNo/deviceName 模糊，deviceType/deviceState 精确；权限：device:record:list")
    @ApiOperationSupport(order = 1)
    @SysLog(module = "设备台账", func = "查询", value = "查询设备台账列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('device:record:list')")
    public Result<PageUtils> list(DeviceRecordForm form) {
        PageUtils page = deviceRecordService.queryPage(form);
        return Result.ok(page);
    }

    /**
     * 登记一台设备（建档）
     */
    @Operation(summary = "登记设备", description = "登记一台杆/闸机进台账；建档状态恒为未接入，后续由设备事件驱动更新；权限：device:record:save")
    @ApiOperationSupport(order = 2)
    @SysLog(module = "设备台账", func = "新增", value = "登记设备建档")
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('device:record:save')")
    public Result<String> save(@RequestBody DeviceRecordForm form) {
        deviceRecordService.saveRecord(form, getUserId());
        return Result.ok();
    }
}
