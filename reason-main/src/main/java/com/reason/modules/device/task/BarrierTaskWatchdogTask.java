package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.filter.TraceIdFilter;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.job.entity.ScheduleJobEntity;
import com.reason.modules.job.service.ScheduleJobLogService;
import com.reason.modules.job.service.ScheduleJobService;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

/**
 * 升降杆任务看护（barrierTaskWatchdogTask，Quartz 每分钟——批次4）
 *
 * <p>看护对象 = schedule_job 中启用的 barrier 家族任务（排除自身）：最后成功时间
 * （schedule_job_log 中 log_state=0 的最大时间）错过 N 次 cron 触发（默认 2）即判停摆 ->
 * ERROR 日志 + JOB_STALLED 告警；恢复执行成功后下一轮自动关闭告警（谁开谁关）。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li>判据用"错过触发次数"而非绝对秒数——自动吸收看护自身的采样点抖动
 *       （看护与目标同频时，绝对秒数阈值边缘会闪报）；</li>
 *   <li>周期从 job_cron 动态解析（管理端改 cron，阈值自动跟随），解析失败记 warn 跳过（不误报）；</li>
 *   <li>数据源=schedule_job_log 权威台账（任务执行历史本就是它的记录，T12 后失败也落痕）——
 *       不改动通用 Quartz 框架 ScheduleJob，数据不双写不漂移；</li>
 *   <li>基线取 max(最后成功时间, 平台启动时刻)：平台重启期间任务自然停摆，旧 lastOk 会被
 *       误判——重启后从启动时刻重新起算宽限（等价"重启后 N 次触发内不报"），
 *       "从未成功过"同样以启动为基线。</li>
 * </ul></p>
 */
@Slf4j
@Component("barrierTaskWatchdogTask")
public class BarrierTaskWatchdogTask implements ITask {

    /** 看护自身 job_id（db/07-barrier-stage4.sql 注册固定值；排除自看护——自己死了没人跑自己） */
    private static final long SELF_JOB_ID = 202L;

    private final ScheduleJobService scheduleJobService;
    private final ScheduleJobLogService scheduleJobLogService;
    private final DeviceAlarmService alarmService;
    private final BarrierProperties properties;

    /** 启动时刻（毫秒）：重启宽限与"从未成功"任务的基线（实例初始化时刻≈进程启动） */
    private final long startupTimeMillis = System.currentTimeMillis();

    public BarrierTaskWatchdogTask(ScheduleJobService scheduleJobService,
                                   ScheduleJobLogService scheduleJobLogService,
                                   DeviceAlarmService alarmService,
                                   BarrierProperties properties) {
        this.scheduleJobService = scheduleJobService;
        this.scheduleJobLogService = scheduleJobLogService;
        this.alarmService = alarmService;
        this.properties = properties;
    }

    @Override
    public void run(String params) {
        //看护轮次链路号（与任务/事件统一可检索）
        MDC.put(TraceIdFilter.MDC_KEY, TraceIdFilter.generateTraceId());
        try {
            doRun();
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }

    private void doRun() {
        //看护对象：启用的 barrier 家族任务（新增 barrier* 任务自动纳入），排除自身
        List<ScheduleJobEntity> watched = scheduleJobService.list(new LambdaQueryWrapper<ScheduleJobEntity>()
                .likeRight(ScheduleJobEntity::getJobBean, "barrier")
                .eq(ScheduleJobEntity::getJobState, 0)
                .ne(ScheduleJobEntity::getJobId, SELF_JOB_ID));
        for (ScheduleJobEntity job : watched) {
            //单目标异常不中断整轮（cron 配置错等）：记日志继续，下轮自愈
            try {
                checkOne(job);
            } catch (Exception e) {
                log.warn("任务看护单目标检查异常，跳过 jobId={} cause={}", job.getJobId(), e.getMessage());
            }
        }
    }

    /**
     * 单任务停摆判定：最后成功时间错过 N 次触发 -> 告警；未错过 -> 关闭残留告警（恢复）
     */
    private void checkOne(ScheduleJobEntity job) {
        String watchdogNo = "__job:" + job.getJobId() + "__";
        //周期从 cron 解析（Quartz 连算两次触发取间隔）；表达式非法=配置错位——记 warn 跳过（不误报）
        long periodMillis;
        try {
            CronExpression cron = new CronExpression(job.getJobCron());
            Date first = cron.getNextValidTimeAfter(new Date());
            Date second = cron.getNextValidTimeAfter(first);
            periodMillis = second.getTime() - first.getTime();
        } catch (ParseException | IllegalArgumentException e) {
            log.warn("任务看护：cron 解析失败跳过 jobId={} cron={} cause={}",
                    job.getJobId(), job.getJobCron(), e.getMessage());
            return;
        }
        //基线 = max(最后成功时间, 平台启动时刻)：重启后旧 lastOk 不作数（重启期间停摆非任务故障）
        Long lastOkSec = scheduleJobLogService.getLastSuccessTime(job.getJobId());
        long baseline = Math.max(lastOkSec != null ? lastOkSec * 1000L : 0L, startupTimeMillis);
        long missed = (System.currentTimeMillis() - baseline) / periodMillis;
        if (missed >= properties.getJobStallMissedTriggers()) {
            String content = String.format("任务停摆：%s(jobId=%d) 最后成功距今错过 %d 次触发（周期 %ds），检查调度与执行器",
                    job.getJobName(), job.getJobId(), missed, periodMillis / 1000);
            alarmService.raise(watchdogNo, AlarmType.JOB_STALLED, content);
            log.error("任务看护发现停摆 jobId={} name={} missed={} periodMillis={}",
                    job.getJobId(), job.getJobName(), missed, periodMillis);
        } else {
            //恢复正常（含从未告警过的稳态）：关闭未处理停摆告警——谁开谁关，空写无害
            alarmService.closeUnhandledAlarm(watchdogNo, AlarmType.JOB_STALLED);
        }
    }
}
