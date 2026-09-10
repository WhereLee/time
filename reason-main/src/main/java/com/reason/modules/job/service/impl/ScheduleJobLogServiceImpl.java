/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.*;
import com.reason.modules.job.form.ScheduleJobLogForm;
import com.reason.modules.job.service.ScheduleJobLogService;
import com.reason.modules.job.dao.ScheduleJobLogDao;
import com.reason.modules.job.entity.ScheduleJobLogEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service("scheduleJobLogService")
public class ScheduleJobLogServiceImpl extends ServiceImpl<ScheduleJobLogDao, ScheduleJobLogEntity> implements ScheduleJobLogService {

	/**
	 * 查询定时任务执行日志-分页
	 * @param form
	 * @return
	 */
	@Override
	public PageUtils queryPage(ScheduleJobLogForm form) {
		IPage<ScheduleJobLogEntity> page = this.page(
			new Query<ScheduleJobLogEntity>().getPage(new MapUtils()
					.put(Constant.PAGE,form.getPage()).put(Constant.LIMIT,form.getLimit())
					.put(Constant.ORDER_FIELD,"log_id").put(Constant.ORDER,"desc")),
			new QueryWrapper<ScheduleJobLogEntity>()
					.like(StringUtils.isNotBlank(form.getJobName()),"job_name", form.getJobName())
					.eq(form.getLogState() != null,"log_state", form.getLogState())
		);

		return new PageUtils(page);
	}

	/**
	 * 根据ID 查询定时任务执行日志
	 * @param logId
	 * @return
	 */
	@Override
	public ScheduleJobLogEntity getInfo(Long logId) {
		//定时任务执行日志
		ScheduleJobLogEntity log = this.getById(logId);

		if (log == null || log.getLogId() == null) {
			throw new RRException("该日志信息不存在或已删除");
		}

		return log;
	}

	/**
	 * 查任务最后成功时间（批次4 任务看护）：权威台账=schedule_job_log，失败也落痕（T12），
	 * 只取 log_state=0 的最大创建时间；无成功记录返回 null
	 */
	@Override
	public Long getLastSuccessTime(Long jobId) {
		Map<String, Object> row = this.getMap(new QueryWrapper<ScheduleJobLogEntity>()
				.select("MAX(log_createtime) AS last_ok")
				.eq("job_id", jobId)
				.eq("log_state", 0));
		if (row == null || row.get("last_ok") == null) {
			return null;
		}
		return ((Number) row.get("last_ok")).longValue();
	}
}
