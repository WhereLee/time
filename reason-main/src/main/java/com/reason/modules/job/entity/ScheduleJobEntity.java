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
import com.reason.modules.job.vo.ScheduleJobVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务
 *
 * @author Mark sunlightcs@gmail.com
 */
@Schema(description = "定时任务对象")
@Data
@TableName("schedule_job")
public class ScheduleJobEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * 任务调度参数key
	 */
    public static final String JOB_PARAM_KEY = "JOB_PARAM_KEY";

	/**
	 * 任务id
	 */
	@Schema(description = "任务ID")
	@TableId
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
	 * 参数
	 */
	@Schema(description = "参数 现未使用")
	private String jobParams;
	/**
	 * cron表达式
	 */
	@Schema(description = "Cron表达式", example = "0 0/10 * * * ? *")
	private String jobCron;
	/**
	 * 任务状态  0：正常  1：暂停
	 */
	@Schema(description = "任务状态 0-正常 1-暂停")
	private Integer jobState;
	/**
	 * 说明、备注
	 */
	@Schema(description = "说明/备注")
	private String jobComment;
	/**
	 * 创建时间戳，单位秒
	 */
	@Schema(description = "创建时间戳（秒）")
	private Long jobCreatetime;
	/**
	 * 更新时间戳，单位秒
	 */
	@Schema(description = "更新时间戳（秒）")
	private Long jobUpdatetime;
	/**
	 * 有效状态 0-有效 >0 无效
	 */
	@Schema(description = "状态标志 0-正常 >0-删除")
	private Long jobStatus;


	public ScheduleJobEntity() {}

	/**
	 * 暂停或恢复
	 * @param jobId
	 * @param jobState
	 */
	public ScheduleJobEntity(Long jobId,Integer jobState) {
		this.jobId = jobId;
		this.jobState = jobState;
		this.jobUpdatetime = System.currentTimeMillis()/1000;
	}

	/**
	 * 删除-将status=id
	 * @param jobId
	 */
	public ScheduleJobEntity(Long jobId) {
		this.jobId = jobId;
		this.jobStatus = jobId;
		this.jobUpdatetime = System.currentTimeMillis()/1000;
	}

	/**
	 * 新增或修改
	 * @param jobVO 前端传入参数对象
	 * @param type 1-新增 2-修改
	 */
	public ScheduleJobEntity(ScheduleJobVO jobVO,Integer type) {
		this.jobBean = jobVO.getJobBean();
		this.jobName = jobVO.getJobName();
		this.jobParams = jobVO.getJobParams();
		this.jobCron = jobVO.getJobCron();
		this.jobComment = jobVO.getJobComment();
		if (type == 1) {
			this.jobCreatetime = System.currentTimeMillis()/1000;
			this.jobUpdatetime = System.currentTimeMillis()/1000;
		} else if (type == 2) {
			this.jobId = jobVO.getJobId();
			this.jobUpdatetime = System.currentTimeMillis()/1000;
		}
	}

	/**
	 * 判断数据是否有效，即未删除
	 * @return true：是
	 */
	public boolean isValid() {
		return (jobStatus != null && jobStatus == 0);
	}

}
