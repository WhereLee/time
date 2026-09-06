package com.reason.modules.sys.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.utils.Result;
import com.reason.modules.sys.form.SysLogForm;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.reason.modules.sys.entity.SysLogEntity;
import com.reason.modules.sys.service.SysLogService;
import com.reason.common.utils.PageUtils;



/**
 * 操作日志 查询操作不记日志
 *
 * @date 2020-04-22 15:02:49
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("sys/log")
public class SysLogController extends AbstractController{
    @Autowired
    private SysLogService sysLogService;

    /**
     * 列表
     */
    @Operation(summary = "列表查询", description = "查询所有操作日志；权限说明：sys:log:list")
    @ApiOperationSupport(order = 10,ignoreParameters = {"areaId","sqlFilter","logState"})
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('sys:log:list')")
    public Result<PageUtils> list(SysLogForm form){
        form.setLogState(0);
        PageUtils page = sysLogService.queryPage(form);

        return Result.ok(page);
    }


    /**
     * 信息
     */
    /*@Operation(summary = "详细查询", description = "查询所选操作日志的详细信息，权限说明：sys:log:info 查询")
    @ApiOperationSupport(order = 30)
    @GetMapping("/info/{logId}")
    @PreAuthorize("hasAuthority('sys:log:info')")
    public Result<SysLogEntity> info(@PathVariable("logId") Long logId){
		SysLogEntity log = sysLogService.getInfo(logId);

        return Result.ok(log);
    }*/


}
