/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.job.utils;

import com.reason.common.utils.StringUtils;
import com.reason.modules.job.service.ScheduleJobLogService;
import com.reason.common.utils.SpringContextUtils;
import com.reason.modules.job.entity.ScheduleJobEntity;
import com.reason.modules.job.entity.ScheduleJobLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.lang.reflect.Method;


/**
 * 定时任务
 *
 * @author Mark sunlightcs@gmail.com
 */
@Slf4j
public class ScheduleJob extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        ScheduleJobEntity scheduleJob = (ScheduleJobEntity) context.getMergedJobDataMap()
        		.get(ScheduleJobEntity.JOB_PARAM_KEY);
        
        //获取spring bean
        ScheduleJobLogService scheduleJobLogService = (ScheduleJobLogService) SpringContextUtils.getBean("scheduleJobLogService");

        //任务开始时间
		Long startTime = System.currentTimeMillis()/1000;

		//数据库保存执行记录
        ScheduleJobLogEntity jobLog = new ScheduleJobLogEntity();
		jobLog.setJobId(scheduleJob.getJobId());
		jobLog.setJobBean(scheduleJob.getJobBean());
		jobLog.setJobName(scheduleJob.getJobName());
		jobLog.setLogCreatetime(startTime);

        try {
            //执行任务
        	//log.info("任务准备执行，任务ID：" + scheduleJob.getJobId());

//			Object target = SpringContextUtils.getBean(scheduleJob.getJobBean());
			String jobBean = scheduleJob.getJobBean();
// 解析规则：截取第一个"-"或"_"前的核心标识（兼容addTask-2/liftingStrategyTask_57_begin）
			String coreBean = jobBean.split("[-_]")[0];
			Object target = SpringContextUtils.getBean(coreBean);

			Method method = target.getClass().getDeclaredMethod("run", String.class);
			method.invoke(target, scheduleJob.getJobParams());
			
			//任务执行总时长
			Long times = System.currentTimeMillis()/1000 - startTime;
			jobLog.setLogDuration(times);
			//任务状态    0：成功    1：失败
			jobLog.setLogState(0);

			//log.info("任务执行完毕，任务ID：" + scheduleJob.getJobId() + "  总共耗时：" + times + "毫秒");
		} catch (Exception e) {
			//log.info("任务执行失败，任务ID：" + scheduleJob.getJobId(), e);
			
			//任务执行总时长
			Long times = System.currentTimeMillis()/1000 - startTime;
			jobLog.setLogDuration(times);
			
			//任务状态    0：成功    1：失败
			jobLog.setLogState(1);
			jobLog.setLogError(StringUtils.substring(e.toString(), 0, 2000));
		}finally {
			scheduleJobLogService.save(jobLog);
		}
    }
}
