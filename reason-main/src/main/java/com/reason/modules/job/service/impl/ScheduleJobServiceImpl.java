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
import com.reason.common.validator.Assert;
import com.reason.common.validator.ValidatorUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import com.reason.modules.job.dao.ScheduleJobDao;
import com.reason.modules.job.entity.ScheduleJobEntity;
import com.reason.modules.job.form.ScheduleJobForm;
import com.reason.modules.job.service.ScheduleJobService;
import com.reason.modules.job.utils.ScheduleUtils;
import com.reason.modules.job.vo.ScheduleJobVO;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.*;

@Slf4j
@Service("scheduleJobService")
public class ScheduleJobServiceImpl extends ServiceImpl<ScheduleJobDao, ScheduleJobEntity> implements ScheduleJobService {
	@Resource
    private Scheduler scheduler;
	
	/**
	 * 项目启动时，初始化定时器
	 */
	@PostConstruct
	public void init(){
		List<ScheduleJobEntity> scheduleJobList = this.listByMap(new MapUtils().put("job_status",0));
		log.info("scheduleJobList:{}",scheduleJobList.size());
		for(ScheduleJobEntity scheduleJob : scheduleJobList){
			CronTrigger cronTrigger = ScheduleUtils.getCronTrigger(scheduler, scheduleJob.getJobId());
            //如果不存在，则创建
            if(cronTrigger == null) {
                ScheduleUtils.createScheduleJob(scheduler, scheduleJob);
            }else {
                ScheduleUtils.updateScheduleJob(scheduler, scheduleJob);
            }
		}
	}

	/**
	 * 查询定时任务信息-分页
	 * @param form
	 * @return
	 */
	@Override
	public PageUtils queryPage(ScheduleJobForm form) {
		IPage<ScheduleJobEntity> page = this.page(
			new Query<ScheduleJobEntity>().getPage(new MapUtils()
					.put(Constant.PAGE,form.getPage()).put(Constant.LIMIT,form.getLimit())),
			new QueryWrapper <ScheduleJobEntity>()
					.eq("job_status", 0)
					.like(StringUtils.isNotBlank(form.getJobBean()),"job_bean", form.getJobBean())
					.like(StringUtils.isNotBlank(form.getJobName()),"job_name", form.getJobName())
					.eq(form.getJobState() != null,"job_state", form.getJobState())
		);

		return new PageUtils(page);
	}

	/**
	 * 根据ID 查询定时任务
	 * @param jobId
	 * @return
	 */
	@Override
	public ScheduleJobEntity getInfo(Long jobId) {
		//查询定时任务
		ScheduleJobEntity job = this.getById(jobId);

		if (job == null || !job.isValid()) {
			throw new RRException("该定时任务信息不存在或已删除");
		}

		return job;
	}

	/**
	 * 新增定时任务
	 * @param jobVO
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveJob(ScheduleJobVO jobVO) {
		//1.校验参数
		ValidatorUtils.validateEntity(jobVO,AddGroup.class);

		//2.创建定时任务实体类
		ScheduleJobEntity job = new ScheduleJobEntity(jobVO,1);

		//3.新增
        this.save(job);

        //4.创建定时任务
		//重新查询-获得完整的job信息
		ScheduleJobEntity scheduleJob = this.getById(job.getJobId());
        ScheduleUtils.createScheduleJob(scheduler, scheduleJob);
    }

	/**
	 * 修改定时任务
	 * @param jobVO
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateJob(ScheduleJobVO jobVO) {
		//1.校验参数
		ValidatorUtils.validateEntity(jobVO,UpdateGroup.class);

		//2.校验 id 是否存在
		ScheduleJobEntity old = this.getById(jobVO.getJobId());
		if (old == null || !old.isValid()) {
			throw new RRException("该定时任务信息不存在或已删除");
		}

		//3.创建定时任务实体类
		ScheduleJobEntity job = new ScheduleJobEntity(jobVO,2);

		//4.修改
		this.updateById(job);

		//5.修改定时任务
		//重新查询-获得完整的job信息
		ScheduleJobEntity scheduleJob = this.getById(job.getJobId());
		ScheduleUtils.updateScheduleJob(scheduler, scheduleJob);
    }

	/**
	 * 删除定时任务
	 * @param jobId
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
    public void deleteJob(Long jobId) {
		//1.校验参数
		Assert.isNull(jobId,"定时任务ID不能为空");

		//2.校验 id 是否存在
		ScheduleJobEntity old = this.getById(jobId);
		if (old == null || !old.isValid()) {
			throw new RRException("该定时任务信息不存在或已删除");
		}

		//3.逻辑删除 status = id
		this.updateById(new ScheduleJobEntity(jobId));

    	//4.删除定时任务
		ScheduleUtils.deleteScheduleJob(scheduler, jobId);
	}

	/**
	 * 根据jobId 立即执行
	 * @param jobId
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void run(Long jobId) {
		//1.校验参数
		Assert.isNull(jobId,"定时任务ID不能为空");

		//2.校验 id 是否存在
		ScheduleJobEntity old = this.getById(jobId);
		if (old == null || !old.isValid()) {
			throw new RRException("该定时任务信息不存在或已删除");
		}

		//3.立即执行
		ScheduleUtils.run(scheduler, old);
	}

	/**
	 * 立即执行 带动态参数
	 * @param job
	 */
	@Override
	public void runWithParams(ScheduleJobEntity job) {
		//1.校验参数
		Assert.isNull(job.getJobId(),"定时任务ID不能为空");

		//2.校验 id 是否存在
		ScheduleJobEntity old = this.getById(job.getJobId());
		if (old == null || !old.isValid()) {
			throw new RRException("该定时任务信息不存在或已删除");
		}

		//3.立即执行
		ScheduleUtils.run(scheduler, job);

	}

	/**
	 * 暂停运行
	 * @param jobId
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void pause(Long jobId) {
		//1.校验参数
		Assert.isNull(jobId,"定时任务ID不能为空");

		//2.校验 id 是否存在
		ScheduleJobEntity old = this.getById(jobId);
		if (old == null || !old.isValid()) {
			throw new RRException("该定时任务信息不存在或已删除");
		}

		//3.暂停
		this.updateById(new ScheduleJobEntity(jobId,1));

		//4.暂停运行
		ScheduleUtils.pauseJob(scheduler, jobId);
	}

	/**
	 * 恢复运行
	 * @param jobId
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void resume(Long jobId) {
		//1.校验参数
		Assert.isNull(jobId,"定时任务ID不能为空");

		//2.校验 id 是否存在
		ScheduleJobEntity old = this.getById(jobId);
		if (old == null || !old.isValid()) {
			throw new RRException("该定时任务信息不存在或已删除");
		}

		//3.恢复
		this.updateById(new ScheduleJobEntity(jobId,0));

		//4.恢复运行
		ScheduleUtils.resumeJob(scheduler, jobId);
	}
    
}
