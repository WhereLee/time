package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.common.filter.TraceIdFilter;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.CommandStatus;
import com.reason.modules.device.enums.DeviceState;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时对账与巡检任务单测（A2 补网）：T20 QUERY_STATE 实况对账三分支
 * （到位销账/动作中不抢/其余重试）+ 重试仲裁分层（保持期/代际裁决/上限告警）
 * + MOVING 卡死巡检 + 单条异常不中断轮次 + 轮次 traceId 生命周期。
 */
@DisplayName("超时对账与巡检任务(A2 补网)")
@ExtendWith(MockitoExtension.class)
class BarrierMonitorTaskTest {

    private static final String DEVICE_NO = "BARRIER-E-01";

    /**
     * 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存
     * （覆盖实现内引用到的全部实体：DeviceRecordEntity + DeviceCommandLogEntity）
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
    private DeviceCommandLogService commandLogService;
    @Mock
    private DeviceCommandService commandService;
    @Mock
    private DeviceAlarmService alarmService;
    @Mock
    private DeviceMonitorService monitorService;
    @Mock
    private DeviceRecordDao recordDao;
    @Mock
    private ManualHoldService manualHoldService;
    @InjectMocks
    private BarrierMonitorTask monitorTask;

    private DeviceCommandLogEntity pendingLog(String deviceNo, long seq, int retryCount) {
        DeviceCommandLogEntity log = new DeviceCommandLogEntity();
        log.setCommandId(100L + seq);
        log.setDeviceNo(deviceNo);
        log.setCommandAction("OPEN");
        log.setCommandSeq(seq);
        log.setRetryCount(retryCount);
        log.setCommandStatus(CommandStatus.PENDING.getCode());
        return log;
    }

    private static DeviceCommandService.QueryResult queryResult(Integer state) {
        DeviceCommandService.QueryResult qr = new DeviceCommandService.QueryResult();
        qr.setState(state);
        return qr;
    }

    /** doRun 每轮必调的三个桩：超时阈值/卡死阈值/无卡 MOVING 记录（scanOffline 为 void 无需桩） */
    private void stubRound() {
        when(properties.getCommandTimeoutSeconds()).thenReturn(30);
        when(properties.getMovingStuckSeconds()).thenReturn(60);
        when(recordDao.selectList(any())).thenReturn(List.of());
    }

    // ---------- 超时对账：实况三分支 ----------

    @Test
    @DisplayName("超时对账：实况查询失败 -> 无实况可依，走重试路径（同 seq 重发）")
    void 对账_查询失败_走重试() {
        stubRound();
        DeviceCommandLogEntity pending = pendingLog(DEVICE_NO, 5, 0);
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pending));
        when(commandService.queryState(DEVICE_NO)).thenReturn(null);

        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(false);
        when(commandLogService.getOne(any())).thenReturn(null);
        when(properties.getMaxRetry()).thenReturn(3);

        monitorTask.run(null);

        ArgumentCaptor<DeviceCommandLogEntity> captor = ArgumentCaptor.forClass(DeviceCommandLogEntity.class);
        verify(commandService).retryPending(captor.capture());
        assertThat(captor.getValue().getCommandSeq()).isEqualTo(5L);
    }

    @Test
    @DisplayName("超时对账：实况=目标态 -> 按 seq 精确销账 ARRIVED，不重试不告警")
    void 对账_实况已到位_销账() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pendingLog(DEVICE_NO, 5, 0)));
        when(commandService.queryState(DEVICE_NO)).thenReturn(queryResult(DeviceState.UP.getCode()));

        monitorTask.run(null);

        verify(commandLogService).markArrivedBySeq(DEVICE_NO, 5L, "OPEN");
        verify(commandService, never()).retryPending(any());
    }

    @Test
    @DisplayName("超时对账：实况=动作中(MOVING) -> 不抢不重试，下轮再看")
    void 对账_动作中_不干预() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pendingLog(DEVICE_NO, 5, 0)));
        when(commandService.queryState(DEVICE_NO)).thenReturn(queryResult(DeviceState.MOVING.getCode()));

        monitorTask.run(null);

        verify(commandLogService, never()).markArrivedBySeq(anyString(), any(Long.class), anyString());
        verify(commandService, never()).retryPending(any());
    }

    @Test
    @DisplayName("超时对账：实况非目标态（DOWN）-> 重试路径")
    void 对账_实况背离_走重试() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pendingLog(DEVICE_NO, 5, 0)));
        when(commandService.queryState(DEVICE_NO)).thenReturn(queryResult(DeviceState.DOWN.getCode()));

        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(false);
        when(commandLogService.getOne(any())).thenReturn(null);
        when(properties.getMaxRetry()).thenReturn(3);

        monitorTask.run(null);

        verify(commandService).retryPending(any(DeviceCommandLogEntity.class));
    }

    // ---------- 重试仲裁分层 ----------

    @Test
    @DisplayName("重试仲裁：手动保持期内 -> 不重试只挂账（人在接管）")
    void 仲裁_保持期_不重试() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pendingLog(DEVICE_NO, 5, 0)));
        when(commandService.queryState(DEVICE_NO)).thenReturn(queryResult(DeviceState.DOWN.getCode()));
        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(true);

        monitorTask.run(null);

        verify(commandService, never()).retryPending(any());
        verify(commandLogService, never()).getOne(any());
    }

    @Test
    @DisplayName("重试仲裁：存在更晚 seq 流水 -> SUPERSEDED 终止挂账，不重试")
    void 仲裁_被新代际取代() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pendingLog(DEVICE_NO, 5, 0)));
        when(commandService.queryState(DEVICE_NO)).thenReturn(queryResult(DeviceState.DOWN.getCode()));
        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(false);
        when(commandLogService.getOne(any())).thenReturn(pendingLog(DEVICE_NO, 9, 0));
        when(commandLogService.markSuperseded(105L)).thenReturn(true);

        monitorTask.run(null);

        verify(commandLogService).markSuperseded(105L);
        verify(commandService, never()).retryPending(any());
    }

    @Test
    @DisplayName("重试仲裁：达上限且 CAS 命中 -> RETRY_EXCEEDED + 告警转人工")
    void 仲裁_达上限_告警() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pendingLog(DEVICE_NO, 5, 3)));
        when(commandService.queryState(DEVICE_NO)).thenReturn(queryResult(DeviceState.DOWN.getCode()));
        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(false);
        when(commandLogService.getOne(any())).thenReturn(null);
        when(properties.getMaxRetry()).thenReturn(3);
        when(commandLogService.markRetryExceeded(105L)).thenReturn(true);

        monitorTask.run(null);

        verify(alarmService).raise(eq(DEVICE_NO), eq(AlarmType.RETRY_EXCEEDED), anyString());
        verify(commandService, never()).retryPending(any());
    }

    @Test
    @DisplayName("重试仲裁：达上限但 CAS 未命中（事件恰好到位）-> 不告警（实际成功）")
    void 仲裁_达上限_CAS未命中不告警() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(pendingLog(DEVICE_NO, 5, 3)));
        when(commandService.queryState(DEVICE_NO)).thenReturn(queryResult(DeviceState.DOWN.getCode()));
        when(manualHoldService.isActive(DEVICE_NO)).thenReturn(false);
        when(commandLogService.getOne(any())).thenReturn(null);
        when(properties.getMaxRetry()).thenReturn(3);
        when(commandLogService.markRetryExceeded(105L)).thenReturn(false);

        monitorTask.run(null);

        verify(alarmService, never()).raise(anyString(), eq(AlarmType.RETRY_EXCEEDED), anyString());
    }

    // ---------- MOVING 卡死巡检 ----------

    @Test
    @DisplayName("MOVING 巡检：台账卡动作中超阈值 -> MOVING_STUCK 告警（只喊不改状态）")
    void 巡检_卡死告警() {
        stubRound();
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of());
        DeviceRecordEntity stuck = new DeviceRecordEntity();
        stuck.setDeviceNo(DEVICE_NO);
        stuck.setDeviceState(DeviceState.MOVING.getCode());
        when(recordDao.selectList(any())).thenReturn(List.of(stuck));

        monitorTask.run(null);

        verify(alarmService).raise(eq(DEVICE_NO), eq(AlarmType.MOVING_STUCK), anyString());
    }

    // ---------- 轮次健壮性 ----------

    @Test
    @DisplayName("单条处理异常不中断整轮：第 1 条炸，第 2 条照常处理")
    void 单条异常_不中断轮次() {
        stubRound();
        DeviceCommandLogEntity first = pendingLog(DEVICE_NO, 5, 0);
        DeviceCommandLogEntity second = pendingLog("BARRIER-W-02", 6, 0);
        when(commandLogService.findTimeoutPending(anyInt())).thenReturn(List.of(first, second));
        when(commandService.queryState(DEVICE_NO)).thenThrow(new RuntimeException("QUERY 超时"));
        when(commandService.queryState("BARRIER-W-02")).thenReturn(null);
        when(manualHoldService.isActive("BARRIER-W-02")).thenReturn(false);
        when(commandLogService.getOne(any())).thenReturn(null);
        when(properties.getMaxRetry()).thenReturn(3);

        monitorTask.run(null);

        ArgumentCaptor<DeviceCommandLogEntity> captor = ArgumentCaptor.forClass(DeviceCommandLogEntity.class);
        verify(commandService).retryPending(captor.capture());
        assertThat(captor.getValue().getDeviceNo()).isEqualTo("BARRIER-W-02");
    }

    @Test
    @DisplayName("轮次 traceId 生命周期：运行期置入 MDC，结束必清理（不泄漏到线程复用）")
    void 轮次_链路号置入与清理() {
        stubRound();
        AtomicReference<String> seenInRound = new AtomicReference<>();
        when(commandLogService.findTimeoutPending(anyInt())).thenAnswer(invocation -> {
            seenInRound.set(MDC.get(TraceIdFilter.MDC_KEY));
            return List.of();
        });

        monitorTask.run(null);

        assertThat(seenInRound.get()).isNotBlank();
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
        verify(monitorService).scanOffline();
    }
}
