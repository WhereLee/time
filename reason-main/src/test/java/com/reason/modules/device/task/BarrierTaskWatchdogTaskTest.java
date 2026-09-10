package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.job.entity.ScheduleJobEntity;
import com.reason.modules.job.service.ScheduleJobLogService;
import com.reason.modules.job.service.ScheduleJobService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务看护测试（批次4）：判据=最后成功时间错过 N 次 cron 触发——
 * 新鲜不告警且关闭残留 / 超期告警 / 从未成功以启动为基线（宽限不误报）/ cron 非法跳过
 */
@DisplayName("任务看护(批次4)")
@ExtendWith(MockitoExtension.class)
class BarrierTaskWatchdogTaskTest {

    /**
     * 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存（见
     * document/pitfalls/mybatis-plus-lambda-cache-unit-test.md）
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ScheduleJobEntity.class);
    }

    @Mock
    private ScheduleJobService scheduleJobService;
    @Mock
    private ScheduleJobLogService scheduleJobLogService;
    @Mock
    private DeviceAlarmService alarmService;
    @Mock
    private BarrierProperties properties;
    @InjectMocks
    private BarrierTaskWatchdogTask watchdogTask;

    private ScheduleJobEntity job(long id, String cron) {
        ScheduleJobEntity job = new ScheduleJobEntity();
        job.setJobId(id);
        job.setJobBean("barrierAutoTask");
        job.setJobName("测试任务" + id);
        job.setJobCron(cron);
        job.setJobState(0);
        return job;
    }

    @Test
    @DisplayName("最后成功新鲜：不告警 + 关闭残留停摆告警（谁开谁关）")
    void 正常_不告警并关闭残留() {
        when(properties.getJobStallMissedTriggers()).thenReturn(2);
        when(scheduleJobService.list(ArgumentMatchers.<Wrapper<ScheduleJobEntity>>any())).thenReturn(List.of(job(200L, "0 * * * * ?")));
        when(scheduleJobLogService.getLastSuccessTime(200L)).thenReturn(System.currentTimeMillis() / 1000);

        watchdogTask.run(null);

        verify(alarmService, never()).raise(anyString(), eq(AlarmType.JOB_STALLED), anyString());
        verify(alarmService).closeUnhandledAlarm(eq("__job:200__"), eq(AlarmType.JOB_STALLED));
    }

    @Test
    @DisplayName("最后成功超期（错过 10 次触发）：raise JOB_STALLED（哨兵号按任务区分）")
    void 停摆_告警() {
        when(properties.getJobStallMissedTriggers()).thenReturn(2);
        when(scheduleJobService.list(ArgumentMatchers.<Wrapper<ScheduleJobEntity>>any())).thenReturn(List.of(job(200L, "0 * * * * ?")));
        //最后成功 10 分钟前（周期 1 分钟 -> 错过 10 次）；启动时刻拨回 1 小时前——
        //生产语义：进程跑得比 lastOk 久（基线取 lastOk）；测试构造的实例"刚启动"若不拨回，
        //基线取启动时刻会把 lastOk 吞掉（宽限设计本身正确，测试须对齐语义）
        when(scheduleJobLogService.getLastSuccessTime(200L)).thenReturn(System.currentTimeMillis() / 1000 - 600);
        ReflectionTestUtils.setField(watchdogTask, "startupTimeMillis", System.currentTimeMillis() - 3_600_000L);

        watchdogTask.run(null);

        verify(alarmService).raise(eq("__job:200__"), eq(AlarmType.JOB_STALLED), anyString());
        verify(alarmService, never()).closeUnhandledAlarm(anyString(), any());
    }

    @Test
    @DisplayName("从未成功过：以启动时刻为基线——启动宽限内不误报")
    void 从未成功_启动宽限不误报() {
        when(properties.getJobStallMissedTriggers()).thenReturn(2);
        when(scheduleJobService.list(ArgumentMatchers.<Wrapper<ScheduleJobEntity>>any())).thenReturn(List.of(job(200L, "0 * * * * ?")));
        when(scheduleJobLogService.getLastSuccessTime(200L)).thenReturn(null);

        watchdogTask.run(null);

        verify(alarmService, never()).raise(anyString(), eq(AlarmType.JOB_STALLED), anyString());
    }

    @Test
    @DisplayName("从未成功且启动已久（时间穿越）：告警（真从未跑过要能发现）")
    void 从未成功且启动已久_告警() {
        when(properties.getJobStallMissedTriggers()).thenReturn(2);
        when(scheduleJobService.list(ArgumentMatchers.<Wrapper<ScheduleJobEntity>>any())).thenReturn(List.of(job(200L, "0 * * * * ?")));
        when(scheduleJobLogService.getLastSuccessTime(200L)).thenReturn(null);
        //时间穿越：把启动时刻拨回 10 分钟前（final 实例字段，反射设置——纯测启动宽限边界）
        ReflectionTestUtils.setField(watchdogTask, "startupTimeMillis", System.currentTimeMillis() - 600_000L);

        watchdogTask.run(null);

        verify(alarmService).raise(eq("__job:200__"), eq(AlarmType.JOB_STALLED), anyString());
    }

    @Test
    @DisplayName("cron 非法：记 warn 跳过不误报（不查 lastOk、不读阈值、不告警）")
    void cron非法_跳过() {
        when(scheduleJobService.list(ArgumentMatchers.<Wrapper<ScheduleJobEntity>>any()))
                .thenReturn(List.of(job(200L, "not-a-cron")));

        watchdogTask.run(null);

        verify(scheduleJobLogService, never()).getLastSuccessTime(anyLong());
        verify(alarmService, never()).raise(anyString(), any(), anyString());
    }
}
