package com.reason.modules.device.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.reason.common.exception.RRException;
import com.reason.modules.device.dao.DeviceOnlineDao;
import com.reason.modules.device.dao.GateManualOpDao;
import com.reason.modules.device.entity.DeviceOnlineEntity;
import com.reason.modules.device.entity.GateManualOpEntity;
import com.reason.modules.device.service.impl.GateManualOpServiceImpl;
import com.reason.modules.parking.service.DeviceCommandClient;
import com.reason.modules.sys.entity.SysUserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 手动抬杆服务测试：审计语义（原因必录/非闸机拒绝/成败均留痕）
 */
@DisplayName("手动抬杆服务（审计留痕）")
@ExtendWith(MockitoExtension.class)
class GateManualOpServiceImplTest {

    @Mock
    private DeviceOnlineDao deviceOnlineDao;
    @Mock
    private GateManualOpDao gateManualOpDao;
    @Mock
    private DeviceCommandClient deviceCommandClient;
    @InjectMocks
    private GateManualOpServiceImpl service;

    private SysUserEntity operator() {
        SysUserEntity u = new SysUserEntity();
        u.setUserId(2L);
        u.setUserName("admin");
        return u;
    }

    private DeviceOnlineEntity gateEntity() {
        DeviceOnlineEntity e = new DeviceOnlineEntity();
        e.setDeviceNo("GATE-E-OUT");
        e.setDeviceType(1); // EXIT_GATE
        e.setBindTarget("E");
        return e;
    }

    @Test
    @DisplayName("手动抬杆成功：指令下发 + 留痕 op_result=0（原因与操作人落库）")
    void 抬杆成功留痕() {
        when(deviceOnlineDao.selectOne(any(Wrapper.class))).thenReturn(gateEntity());
        when(deviceCommandClient.sendDeviceCommand(eq(DeviceCommandClient.CMD_OPEN_GATE), eq("GATE-E-OUT")))
                .thenReturn(true);

        boolean sent = service.liftGate("GATE-E-OUT", "浙B8K521", "出口杆故障，人工放行", operator());

        assertThat(sent).isTrue();
        ArgumentCaptor<GateManualOpEntity> captor = ArgumentCaptor.forClass(GateManualOpEntity.class);
        verify(gateManualOpDao).insert(captor.capture());
        GateManualOpEntity op = captor.getValue();
        assertThat(op.getOpResult()).isEqualTo(0);
        assertThat(op.getDeviceNo()).isEqualTo("GATE-E-OUT");
        assertThat(op.getGateCode()).isEqualTo("E");
        assertThat(op.getPlateNo()).isEqualTo("浙B8K521");
        assertThat(op.getOperatorName()).isEqualTo("admin");
        assertThat(op.getOpReason()).isEqualTo("出口杆故障，人工放行");
    }

    @Test
    @DisplayName("设备不可达：下发失败仍留痕 op_result=1（审计不因设备失联而缺失）")
    void 设备不可达留失败痕() {
        when(deviceOnlineDao.selectOne(any(Wrapper.class))).thenReturn(gateEntity());
        when(deviceCommandClient.sendDeviceCommand(eq(DeviceCommandClient.CMD_OPEN_GATE), eq("GATE-E-OUT")))
                .thenReturn(false);

        boolean sent = service.liftGate("GATE-E-OUT", null, "设备故障", operator());

        assertThat(sent).isFalse();
        ArgumentCaptor<GateManualOpEntity> captor = ArgumentCaptor.forClass(GateManualOpEntity.class);
        verify(gateManualOpDao).insert(captor.capture());
        assertThat(captor.getValue().getOpResult()).isEqualTo(1);
        assertThat(captor.getValue().getOpRemark()).contains("失败");
    }

    @Test
    @DisplayName("非闸机设备拒绝抬杆（位检/桩无抬杆语义）且不留痕")
    void 非闸机拒绝() {
        DeviceOnlineEntity sensor = new DeviceOnlineEntity();
        sensor.setDeviceNo("SENSOR-A-001");
        sensor.setDeviceType(2); // SENSOR
        when(deviceOnlineDao.selectOne(any(Wrapper.class))).thenReturn(sensor);

        assertThatThrownBy(() -> service.liftGate("SENSOR-A-001", null, "测试", operator()))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非闸机设备");
        verify(gateManualOpDao, never()).insert(any(GateManualOpEntity.class));
        verify(deviceCommandClient, never()).sendDeviceCommand(any(), any());
    }

    @Test
    @DisplayName("原因必录：审计要求不满足直接拒绝，不触达设备")
    void 原因必录() {
        assertThatThrownBy(() -> service.liftGate("GATE-E-OUT", null, "  ", operator()))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("原因");
        verify(deviceOnlineDao, never()).selectOne(any());
        verify(gateManualOpDao, never()).insert(any(GateManualOpEntity.class));
    }
}
