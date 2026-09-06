/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.job.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 定时任务日志
 *
 * @author Mark sunlightcs@gmail.com
 */
@Schema(description = "定时任务日志对象")
@Data
@TableName("schedule_job_log")
public class ScheduleJobLogEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 任务日志id
	 */
	@Schema(description = "日志ID")
	@TableId
	private Long logId;
	/**
	 * 任务id
	 */
	@Schema(description = "任务ID")
	private Long jobId;
	/**
	 * spring bean名称
	 */
	@Schema(description = "Spring Bean名称")
	private String jobBean;
	/**
	 * 任务名称
	 */
	@Schema(description = "任务名称")
	private String jobName;
	/**
	 * 执行状态    0：成功    1：失败
	 */
	@Schema(description = "执行状态 0-成功 1-失败")
	private Integer logState;
	/**
	 * 失败信息
	 */
	@Schema(description = "失败信息")
	private String logError;
	/**
	 * 执行时长 单位 毫秒
	 */
	@Schema(description = "执行时长（毫秒）")
	private Long logDuration;
	/**
	 * 创建时间戳，单位秒
	 */
	@Schema(description = "创建时间戳（秒）")
	private Long logCreatetime;
	
}
