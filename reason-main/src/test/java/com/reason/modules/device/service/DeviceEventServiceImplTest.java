package com.reason.modules.device.service;

import com.reason.common.exception.RRException;
import com.reason.modules.device.config.DeviceBootGenerationGuard;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.form.DeviceEventForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备事件处理编排测试（协议 v2 语义——0.1 核心）：
 * 证据驱动销账（有 commandSeq 才按 seq 闭环）、外力改态不动流水、
 * 旧序事件被序守卫拒绝后幂等丢弃不推进任何流水、FAULT 中断在途+告警
 */
@DisplayName("设备事件编排(协议v2)")
@ExtendWith(MockitoExtension.class)
class DeviceEventServiceImplTest {

    @Mock
    private DeviceRecordService deviceRecordService;
    @Mock
    private DeviceCommandLogService commandLogService;
    @Mock
    private DeviceAlarmService deviceAlarmService;
    @Mock
    private DeviceBootGenerationGuard bootGenerationGuard;
    @InjectMocks
    private com.reason.modules.device.service.impl.DeviceEventServiceImpl eventService;

    private DeviceEventForm event(int state, Long commandSeq, String bootId, long eventSeq) {
        DeviceEventForm form = new DeviceEventForm();
        form.setDeviceNo("BARRIER-E-01");
        form.setState(state);
        form.setCommandSeq(commandSeq);
        form.setBootId(bootId);
        form.setEventSeq(eventSeq);
        return form;
    }

    @Test
    @DisplayName("UP+commandSeq：序更新台账 + 按 seq 精确销账 OPEN 流水（不再按动作猜）")
    void up带指令引用_按seq销账() {
        when(deviceRecordService.updateStateByEventWithSeq("BARRIER-E-01", 1, "boot-1", 7)).thenReturn(true);
        eventService.handleStateEvent(event(1, 7L, "boot-1", 7));
        verify(deviceRecordService).updateStateByEventWithSeq("BARRIER-E-01", 1, "boot-1", 7);
        verify(commandLogService).markArrivedBySeq("BARRIER-E-01", 7, "OPEN");
    }

    @Test
    @DisplayName("UP 无 commandSeq（外力改态）：只更新台账，不销任何流水")
    void up无指令引用_不动流水() {
        when(deviceRecordService.updateStateByEventWithSeq("BARRIER-E-01", 1, "boot-1", 5)).thenReturn(true);
        eventService.handleStateEvent(event(1, null, "boot-1", 5));
        verify(commandLogService, never()).markArrivedBySeq(anyString(), eq(0L), anyString());
    }

    @Test
    @DisplayName("同代际旧序事件（重放/乱序）被序守卫拒绝：幂等丢弃，不推进流水不告警")
    void 旧序重放_幂等丢弃() {
        when(deviceRecordService.updateStateByEventWithSeq("BARRIER-E-01", 1, "boot-1", 3)).thenReturn(false);
        eventService.handleStateEvent(event(1, 9L, "boot-1", 3));
        //关键：重放的到位事件绝不能销掉流水（伪造销账路径在此被封死）
        verify(commandLogService, never()).markArrivedBySeq(anyString(), eq(0L), anyString());
        verify(deviceAlarmService, never()).raise(anyString(), eq(AlarmType.DEVICE_FAULT), anyString());
    }

    @Test
    @DisplayName("MOVING 中间态：只更新台账，流水继续等到位")
    void moving_只更新台账() {
        when(deviceRecordService.updateStateByEventWithSeq("BARRIER-E-01", 3, "boot-1", 4)).thenReturn(true);
        eventService.handleStateEvent(event(3, 7L, "boot-1", 4));
        verify(commandLogService, never()).markArrivedBySeq(anyString(), eq(0L), anyString());
        verify(commandLogService, never()).markExecFailed(anyString());
    }

    @Test
    @DisplayName("FAULT 无 seq（外力/心跳语义）：设备级全断在途 + 故障告警交人工")
    void fault_中断在途并告警() {
        when(deviceRecordService.updateStateByEventWithSeq("BARRIER-E-01", 4, "boot-1", 8)).thenReturn(true);
        eventService.handleStateEvent(event(4, null, "boot-1", 8));
        verify(commandLogService).markExecFailed("BARRIER-E-01");
        verify(commandLogService, never()).markExecFailedBySeq(anyString(), eq(0L));
        verify(deviceAlarmService).raise(eq("BARRIER-E-01"), eq(AlarmType.DEVICE_FAULT), anyString());
    }

    @Test
    @DisplayName("FAULT+commandSeq（0.6）：按 seq 归属中断该条，不做设备级全断")
    void fault带seq_按seq归属中断() {
        when(deviceRecordService.updateStateByEventWithSeq("BARRIER-E-01", 4, "boot-1", 9)).thenReturn(true);
        eventService.handleStateEvent(event(4, 3L, "boot-1", 9));
        verify(commandLogService).markExecFailedBySeq("BARRIER-E-01", 3);
        verify(commandLogService, never()).markExecFailed(anyString());
        verify(deviceAlarmService).raise(eq("BARRIER-E-01"), eq(AlarmType.DEVICE_FAULT), anyString());
    }

    @Test
    @DisplayName("批次8 跨代重放：已见代际事件被拒——不更新台账、不销流水、不告警（重放不可换状态/销账）")
    void 已见代际重放_拒绝() {
        when(bootGenerationGuard.isReplay("BARRIER-E-01", "boot-old")).thenReturn(true);

        eventService.handleStateEvent(event(1, 7L, "boot-old", 99));

        verify(deviceRecordService, never())
                .updateStateByEventWithSeq(anyString(), anyInt(), anyString(), anyLong());
        verify(commandLogService, never()).markArrivedBySeq(anyString(), eq(0L), anyString());
        verify(deviceAlarmService, never()).raise(anyString(), eq(AlarmType.DEVICE_FAULT), anyString());
    }

    @Test
    @DisplayName("批次8 代际登记：台账接受后登记该代际（后续重放判定依据）")
    void 接受后登记代际() {
        when(bootGenerationGuard.isReplay("BARRIER-E-01", "boot-1")).thenReturn(false);
        when(deviceRecordService.updateStateByEventWithSeq("BARRIER-E-01", 1, "boot-1", 7)).thenReturn(true);

        eventService.handleStateEvent(event(1, 7L, "boot-1", 7));

        verify(bootGenerationGuard).register("BARRIER-E-01", "boot-1");
    }

    @Test
    @DisplayName("缺 eventSeq（批次8）：协议垃圾 -> RRException（HTTP 400/MQ 毒消息），不再拆箱 NPE 出 500")
    void 缺eventSeq_协议拒绝() {
        DeviceEventForm form = event(1, 7L, "boot-1", 7);
        form.setEventSeq(null);
        assertThrows(RRException.class, () -> eventService.handleStateEvent(form));
        verifyNoInteractions(deviceRecordService, commandLogService, deviceAlarmService);
    }

    @Test
    @DisplayName("缺 bootId（批次8）：协议垃圾 -> RRException，不进台账更新")
    void 空bootId_协议拒绝() {
        DeviceEventForm form = event(1, 7L, "", 7L);
        assertThrows(RRException.class, () -> eventService.handleStateEvent(form));
        verifyNoInteractions(deviceRecordService, commandLogService, deviceAlarmService);
    }

    @Test
    @DisplayName("缺 state（批次8）：协议垃圾 -> RRException，不进台账更新")
    void 缺state_协议拒绝() {
        DeviceEventForm form = event(1, 7L, "boot-1", 7L);
        form.setState(null);
        assertThrows(RRException.class, () -> eventService.handleStateEvent(form));
        verifyNoInteractions(deviceRecordService, commandLogService, deviceAlarmService);
    }
}
