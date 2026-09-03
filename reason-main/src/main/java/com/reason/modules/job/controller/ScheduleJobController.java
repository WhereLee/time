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
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.job.entity.ScheduleJobEntity;
import com.reason.modules.job.form.ScheduleJobForm;
import com.reason.modules.job.service.ScheduleJobService;
import com.reason.modules.job.vo.ScheduleJobVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 定时任务
 *
 * @author Mark sunlightcs@gmail.com
 */
@Slf4j
@Tag(name = "定时任务")
@RestController
@RequestMapping("/sys/schedule")
public class ScheduleJobController {
	@Autowired
	private ScheduleJobService scheduleJobService;
	
	/**
	 * 定时任务列表
	 */
	@Operation(summary = "列表查询", description = "查询所有定时任务信息-分页；权限说明：sys:schedule:list 查询")
	@ApiOperationSupport(order = 1,ignoreParameters = {"areaId","sqlFilter"})
	@SysLog(module = "定时任务",func = "查询",value = "列表查询定时任务")
	@GetMapping("/list")
	@RequiresPermissions("sys:schedule:list")
	public Result<PageUtils> list(ScheduleJobForm form){
		PageUtils page = scheduleJobService.queryPage(form);

		return Result.ok(page);
	}
	
	/**
	 * 定时任务信息
	 */
	@Operation(summary = "详细查询", description = "查询所选定时任务的详细信息，权限说明：sys:schedule:info 查询")
	@ApiOperationSupport(order = 30)
	@SysLog(module = "定时任务",func = "查询",value = "查询定时任务详细")
	@GetMapping("/info/{jobId}")
	@RequiresPermissions("sys:schedule:info")
	public Result<ScheduleJobEntity> info(@PathVariable("jobId") Long jobId){
		ScheduleJobEntity job = scheduleJobService.getInfo(jobId);
		
		return Result.ok(job);
	}
	
	/**
	 * 保存定时任务
	 */
	@Operation(summary = "新增接口", description = "新增定时任务，权限说明：sys:schedule:save 新增")
	@ApiOperationSupport(order = 40,ignoreParameters = {"jobId"})
	@SysLog(module = "定时任务",func = "新增",value = "新增定时任务")
	@PostMapping("/save")
	@RequiresPermissions("sys:schedule:save")
	public Result save(@RequestBody ScheduleJobVO jobVO){
		scheduleJobService.saveJob(jobVO);
		
		return Result.ok();
	}
	
	/**
	 * 修改定时任务
	 */
	@Operation(summary = "修改接口", description = "修改定时任务，权限说明：sys:schedule:update 修改")
	@ApiOperationSupport(order = 50)
	@SysLog(module = "定时任务",func = "修改",value = "修改定时任务")
	@PostMapping("/update")
	@RequiresPermissions("sys:schedule:update")
	public Result update(@RequestBody ScheduleJobVO jobVO){
		scheduleJobService.updateJob(jobVO);
		
		return Result.ok();
	}
	
	/**
	 * 删除定时任务
	 */
	@Operation(summary = "删除接口", description = "删除定时任务，权限说明：sys:schedule:delete 删除")
	@ApiOperationSupport(order = 60)
	@SysLog(module = "定时任务",func = "删除",value = "删除定时任务")
	@PostMapping("/delete/{jobId}")
	@RequiresPermissions("sys:schedule:delete")
	public Result delete(@PathVariable("jobId") Long jobId){
		scheduleJobService.deleteJob(jobId);
		
		return Result.ok();
	}
	
	/**
	 * 立即执行任务
	 */
	@Operation(summary = "执行接口", description = "立即执行定时任务，权限说明：sys:schedule:run 执行")
	@ApiOperationSupport(order = 70)
	@SysLog(module = "定时任务",func = "执行",value = "立即执行定时任务")
	@PostMapping("/run/{jobId}")
	@RequiresPermissions("sys:schedule:run")
	public Result run(@PathVariable("jobId") Long jobId){
		scheduleJobService.run(jobId);
		
		return Result.ok();
	}
	
	/**
	 * 暂停定时任务
	 */
	@Operation(summary = "暂停接口", description = "暂停定时任务，权限说明：sys:schedule:pause 暂停")
	@ApiOperationSupport(order = 80)
	@SysLog(module = "定时任务",func = "暂停",value = "暂停定时任务")
	@PostMapping("/pause/{jobId}")
	@RequiresPermissions("sys:schedule:pause")
	public Result pause(@PathVariable("jobId") Long jobId){
		scheduleJobService.pause(jobId);
		
		return Result.ok();
	}
	
	/**
	 * 恢复定时任务
	 */
	@Operation(summary = "恢复接口", description = "恢复定时任务，权限说明：sys:schedule:resume 恢复")
	@ApiOperationSupport(order = 80)
	@SysLog(module = "定时任务",func = "恢复",value = "恢复定时任务")
	@PostMapping("/resume/{jobId}")
	@RequiresPermissions("sys:schedule:resume")
	public Result resume(@PathVariable("jobId") Long jobId){
		scheduleJobService.resume(jobId);
		
		return Result.ok();
	}

}
