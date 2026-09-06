/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.job.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.modules.job.entity.ScheduleJobEntity;
import com.reason.common.utils.PageUtils;
import com.reason.modules.job.form.ScheduleJobForm;
import com.reason.modules.job.vo.ScheduleJobVO;

import java.util.Map;

/**
 * 定时任务
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface ScheduleJobService extends IService<ScheduleJobEntity> {

	/**
	 * 查询定时任务信息-分页
	 * @param form
	 * @return
	 */
	PageUtils queryPage(ScheduleJobForm form);

	/**
	 * 根据ID 查询定时任务
	 * @param jobId
	 * @return
	 */
	ScheduleJobEntity getInfo(Long jobId);

	/**
	 * 新增定时任务
	 * @param jobVO
	 */
	void saveJob(ScheduleJobVO jobVO);

	/**
	 * 修改定时任务
	 * @param jobVO
	 */
	void updateJob(ScheduleJobVO jobVO);

	/**
	 * 删除定时任务
	 * @param jobId
	 */
	void deleteJob(Long jobId);

	/**
	 * 根据jobId 立即执行
	 * @param jobId
	 */
	void run(Long jobId);

	/**
	 * 立即执行 带动态参数
	 * @param job
	 */
	void runWithParams(ScheduleJobEntity job);

	/**
	 * 暂停运行
	 * @param jobId
	 */
	void pause(Long jobId);

	/**
	 * 恢复运行
	 * @param jobId
	 */
	void resume(Long jobId);
}
