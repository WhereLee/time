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

	/**
	 * 查任务最后成功时间（批次4 任务看护）：log_state=0 的最大 log_createtime（秒），
	 * 无成功记录返回 null——数据源=执行历史台账（不改动通用 Quartz 框架）
	 * @param jobId
	 * @return
	 */
	Long getLastSuccessTime(Long jobId);
	
}
