/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.job.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.Result;
import com.reason.modules.job.form.ScheduleJobLogForm;
import com.reason.modules.job.service.ScheduleJobLogService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.job.entity.ScheduleJobLogEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 定时任务日志
 *
 * @author Mark sunlightcs@gmail.com
 */
@Tag(name = "定时任务日志")
@RestController
@RequestMapping("/sys/schedulelog")
public class ScheduleJobLogController {
	@Autowired
	private ScheduleJobLogService scheduleJobLogService;
	
	/**
	 * 定时任务日志列表
	 */
	@Operation(summary = "列表查询", description = "查询所有定时任务日志信息-分页；权限说明：sys:schedulelog:list 查询")
	@ApiOperationSupport(order = 1,ignoreParameters = {"areaId","sqlFilter"})
	@SysLog(module = "定时任务日志",func = "查询",value = "列表查询定时任务日志")
	@GetMapping("/list")
	@PreAuthorize("hasAuthority('sys:schedulelog:list')")
	public Result<PageUtils> list(ScheduleJobLogForm form){
		PageUtils page = scheduleJobLogService.queryPage(form);
		
		return Result.ok(page);
	}
	
	/**
	 * 定时任务日志信息
	 */
	/*
	@Operation(summary = "详细查询", description = "查询所选定时任务日志的详细信息，权限说明：sys:schedulelog:info 查询")
	@ApiOperationSupport(order = 30)
	@SysLog(module = "定时任务日志",func = "查询",value = "查询定时任务日志详细")
	@GetMapping("/info/{logId}")
	@PreAuthorize("hasAuthority('sys:schedulelog:info')")
	public Result<ScheduleJobLogEntity> info(@PathVariable("logId") Long logId){
		ScheduleJobLogEntity log = scheduleJobLogService.getInfo(logId);
		
		return Result.ok(log);
	}
	*/
}
