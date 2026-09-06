/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.job.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.job.entity.ScheduleJobLogEntity;
import com.reason.modules.job.form.ScheduleJobLogForm;


/**
 * 定时任务日志
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface ScheduleJobLogService extends IService<ScheduleJobLogEntity> {

	/**
	 * 查询定时任务执行日志-分页
	 * @param form
	 * @return
	 */
	PageUtils queryPage(ScheduleJobLogForm form);

	/**
	 * 根据ID 查询定时任务执行日志
	 * @param logId
	 * @return
	 */
	ScheduleJobLogEntity getInfo(Long logId);
	
}
