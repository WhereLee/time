package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.CommandStatus;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.BarrierTimeRule;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceCommandService;
import com.reason.modules.device.service.DeviceMonitorService;
import com.reason.modules.device.service.ManualHoldService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动对账任务测试（批次4 连续异常升级）：单轮异常不升级、连续达阈值升级
 * RECONCILE_ERROR、成功轮清计数并关闭告警（批次3 Redis 事故暴露的静默缺口）
 */
@DisplayName("自动对账任务(批次4 连续异常升级)")
@ExtendWith(MockitoExtension.class)
class BarrierAutoTaskTest {

    private static final String DEVICE_NO = "BARRIER-E-01";

    /**
     * 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存（见
     * document/pitfalls/mybatis-plus-lambda-cache-unit-test.md——覆盖实现内引用到的全部实体）
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeviceRecordEntity.class);
        TableInfoHelper.initTableInfo(assistant, DeviceCommandLogEntity.class);
    }

    @Mock
    private BarrierProperties properties;
    @Mock
    private BarrierTimeRule timeRule;
    @Mock
    private DeviceRecordDao recordDao;
    @Mock
    private DeviceMonitorService monitorService;
    @Mock
    private DeviceCommandService commandService;
    @Mock
    private DeviceCommandLogService commandLogService;
    @Mock
    private DeviceAlarmService alarmService;
    @Mock
    private ManualHoldService manualHoldService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @InjectMocks
    private BarrierAutoTask autoTask;

    private DeviceRecordEntity record(int state) {
        DeviceRecordEntity record = new DeviceRecordEntity();
        record.setDeviceNo(DEVICE_NO);
        record.setDeviceState(state);
        return record;
    }

    /** 走到"下发校正"前的公共桩：应然 UP、单台设备、无手动保持、非熔断、在线 */
    private void stubBeforeSend(int actualState) {
        when(properties.isAutoEnabled()).thenReturn(true);
        when(timeRule.desiredState(any())).thenReturn(DeviceState.UP.getCode());
        when(recordDao.selectList(any())).thenReturn(List.of(record(actualState)));
        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(false);
        //熔断窗口阈值：mock int 默认 0 会被判"配置错位"（实现侧已防御但仍须给正常值对齐语义）
        when(properties.getAutoFailStreakThreshold()).thenReturn(3);
        when(commandLogService.list(ArgumentMatchers.<Wrapper<DeviceCommandLogEntity>>any())).thenReturn(List.of());
        when(monitorService.isOnline(DEVICE_NO)).thenReturn(true);
    }

    @Test
    @DisplayName("单台异常第 1 轮：计数 1 < 阈值 5，不升级告警（抓持续坏不抓偶发抖）")
    void 单轮异常_不升级() {
        stubBeforeSend(DeviceState.DOWN.getCode());
        doThrow(new RuntimeException("Duplicate entry 'BARRIER-E-01-2' for key 'u_device_seq'"))
                .when(commandService).sendByRule(DEVICE_NO, "OPEN");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(properties.getReconcileFailStreakThreshold()).thenReturn(5);

        autoTask.run(null);

        verify(alarmService, never()).raise(anyString(), eq(AlarmType.RECONCILE_ERROR), anyString());
    }

    @Test
    @DisplayName("连续异常第 5 轮：计数达阈值 -> 升级 RECONCILE_ERROR 告警（静默缺口补上）")
    void 连续异常_升级告警() {
        stubBeforeSend(DeviceState.DOWN.getCode());
        doThrow(new RuntimeException("Duplicate entry 'BARRIER-E-01-2' for key 'u_device_seq'"))
                .when(commandService).sendByRule(DEVICE_NO, "OPEN");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(5L);
        when(properties.getReconcileFailStreakThreshold()).thenReturn(5);

        autoTask.run(null);

        verify(alarmService).raise(eq(DEVICE_NO), eq(AlarmType.RECONCILE_ERROR), anyString());
    }

    @Test
    @DisplayName("成功轮（应然=实然）：清连续异常计数 + 关闭未处理升级告警（谁开谁关）")
    void 成功轮_清理与关闭() {
        stubBeforeSend(DeviceState.UP.getCode());

        autoTask.run(null);

        verify(stringRedisTemplate).delete(eq(BarrierRedisKeys.RECONCILE_FAIL_PREFIX + DEVICE_NO));
        verify(alarmService).closeUnhandledAlarm(eq(DEVICE_NO), eq(AlarmType.RECONCILE_ERROR));
    }

    @Test
    @DisplayName("批次4 恢复标记完善：熔断窗口全失败但设备已到位（静默恢复）-> 放行 + 关闭残留告警")
    void 到位设备_解除熔断态() {
        when(properties.isAutoEnabled()).thenReturn(true);
        when(timeRule.desiredState(any())).thenReturn(DeviceState.UP.getCode());
        when(recordDao.selectList(any())).thenReturn(List.of(record(DeviceState.UP.getCode())));
        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(false);
        //批次4 顺序调整后：离线设备直接跳过（熔断只对在线设备评估）——本用例设备在线
        when(monitorService.isOnline(DEVICE_NO)).thenReturn(true);
        when(properties.getAutoFailStreakThreshold()).thenReturn(3);
        //窗口 3 条 AUTO_RULE 全非 ARRIVED（历史失败），但设备已到位（UP=应然 UP）——静默恢复场景
        when(commandLogService.list(ArgumentMatchers.<Wrapper<DeviceCommandLogEntity>>any()))
                .thenReturn(List.of(failedLog(1L), failedLog(2L), failedLog(3L)));

        autoTask.run(null);

        //不熔断不告警（设备无需校正），残留校正失败告警关闭（归位即恢复）
        verify(alarmService, never()).raise(eq(DEVICE_NO), eq(AlarmType.AUTO_CORRECT_FAILED), anyString());
        verify(alarmService).closeUnhandledAlarm(eq(DEVICE_NO), eq(AlarmType.AUTO_CORRECT_FAILED));
    }

    @Test
    @DisplayName("P4 剧本暴露：超长异常消息（撞索引 SQL 全文）-> content 截断不破主循环")
    void 超长异常_内容截断不抛出() {
        stubBeforeSend(DeviceState.DOWN.getCode());
        //模拟 MyBatis 撞唯一索引的异常：message=完整 SQL 文本（数千字符）
        String longMsg = "### The error may involve defaultParameterMap ### SQL: INSERT INTO device_command_log ".repeat(80);
        doThrow(new RuntimeException(longMsg)).when(commandService).sendByRule(DEVICE_NO, "OPEN");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(5L);
        when(properties.getReconcileFailStreakThreshold()).thenReturn(5);

        autoTask.run(null); // 不抛 = 通过

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(alarmService).raise(eq(DEVICE_NO), eq(AlarmType.RECONCILE_ERROR), captor.capture());
        assertThat(captor.getValue().length()).isLessThanOrEqualTo(512);
    }

    private DeviceCommandLogEntity failedLog(long seq) {
        DeviceCommandLogEntity log = new DeviceCommandLogEntity();
        log.setCommandSeq(seq);
        log.setCommandStatus(CommandStatus.SEND_FAILED.getCode());
        return log;
    }
}
