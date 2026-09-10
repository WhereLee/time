package com.reason.modules.device.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.service.impl.DeviceMetricsServiceImpl;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 业务指标采集测试（批次4 D-D 第 0 档）：四项指标聚合 + 任务视图派生（距今秒数）；
 * 无接入设备时在线率给占位不除零
 */
@DisplayName("业务指标采集(批次4)")
@ExtendWith(MockitoExtension.class)
class DeviceMetricsServiceImplTest {

    /**
     * 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存（见
     * document/pitfalls/mybatis-plus-lambda-cache-unit-test.md——覆盖实现内引用到的全部实体）
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeviceRecordEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleJobEntity.class);
    }

    @Mock
    private DeviceCommandLogService commandLogService;
    @Mock
    private DeviceMonitorService monitorService;
    @Mock
    private DeviceAlarmService alarmService;
    @Mock
    private DeviceRecordDao recordDao;
    @Mock
    private ScheduleJobService scheduleJobService;
    @Mock
    private ScheduleJobLogService scheduleJobLogService;
    @InjectMocks
    private DeviceMetricsServiceImpl metricsService;

    @Test
    @DisplayName("聚合快照：四项指标 + 任务视图（距今秒数派生）")
    void 聚合_四项与任务视图() {
        when(commandLogService.countPending()).thenReturn(2L);
        when(recordDao.selectCount(any())).thenReturn(50L);
        when(monitorService.countOnline()).thenReturn(45);
        when(alarmService.countUnhandled()).thenReturn(3L);
        ScheduleJobEntity job = new ScheduleJobEntity();
        job.setJobId(200L);
        job.setJobName("升降杆自动规则对账");
        job.setJobState(0);
        when(scheduleJobService.list(ArgumentMatchers.<Wrapper<ScheduleJobEntity>>any())).thenReturn(List.of(job));
        when(scheduleJobLogService.getLastSuccessTime(200L)).thenReturn(System.currentTimeMillis() / 1000 - 12);

        Map<String, Object> m = metricsService.collect();

        assertThat(m.get("commandPending")).isEqualTo(2L);
        assertThat(m.get("deviceProvisioned")).isEqualTo(50L);
        assertThat(m.get("deviceOnline")).isEqualTo(45);
        assertThat(m.get("onlineRate")).isEqualTo("90.0%");
        assertThat(m.get("unhandledAlarms")).isEqualTo(3L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) m.get("barrierTasks");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).get("jobId")).isEqualTo(200L);
        assertThat((Long) tasks.get(0).get("lastSuccessAgoSeconds")).isBetween(11L, 13L);
    }

    @Test
    @DisplayName("无接入设备：在线率给 '-'（分母为零不除）")
    void 无接入_在线率占位() {
        when(commandLogService.countPending()).thenReturn(0L);
        when(recordDao.selectCount(any())).thenReturn(0L);
        when(monitorService.countOnline()).thenReturn(0);
        when(alarmService.countUnhandled()).thenReturn(0L);
        when(scheduleJobService.list(ArgumentMatchers.<Wrapper<ScheduleJobEntity>>any())).thenReturn(List.of());

        Map<String, Object> m = metricsService.collect();

        assertThat(m.get("onlineRate")).isEqualTo("-");
    }
}
