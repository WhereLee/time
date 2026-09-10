package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.Result;
import com.reason.modules.device.service.DeviceMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备域业务指标接口（批次4 D-D 第 0 档）
 *
 * <p>只读运维视图：DB/Redis 实时派生（无缓存）——指令积压 PENDING/在线率/任务最后成功时间/
 * 未处理告警数。第 0 档原则不引 Prometheus/Micrometer 注册表（单体样例的重量级链路框架=装饰）。
 * 权限复用 device:record:list：同一管理端设备只读视图的聚合，不为演示功能新增 RBAC 噪声。</p>
 */
@Tag(name = "设备指标")
@RestController
@RequestMapping("device/metrics")
public class DeviceMetricsController {

    @Autowired
    private DeviceMetricsService deviceMetricsService;

    /**
     * 业务指标快照
     */
    @Operation(summary = "业务指标快照", description = "指令积压 PENDING/在线率/任务最后成功时间/未处理告警数；权限：device:record:list")
    @ApiOperationSupport(order = 1)
    @SysLog(module = "设备指标", func = "查询", value = "查询设备域业务指标")
    @GetMapping
    @PreAuthorize("hasAuthority('device:record:list')")
    public Result<Map<String, Object>> metrics() {
        return Result.ok(deviceMetricsService.collect());
    }
}
