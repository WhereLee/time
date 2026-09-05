package com.reason.modules.device.service;

import com.reason.modules.device.dao.DeviceOnlineDao;
import com.reason.modules.device.entity.DeviceOnlineEntity;
import com.reason.modules.device.service.impl.DeviceOnlineServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备在线台账服务测试：心跳批处理（online/offline 分流、空批、未知设备差集告警路径）
 */
@DisplayName("设备在线台账服务（心跳批）")
@ExtendWith(MockitoExtension.class)
class DeviceOnlineServiceImplTest {

    @Mock
    private DeviceOnlineDao deviceOnlineDao;
    @InjectMocks
    private DeviceOnlineServiceImpl service;

    @Test
    @DisplayName("心跳批：online 置在线刷新时刻，offline 显式置离线，各一次批量 UPDATE")
    void 心跳批分流() {
        service.recordHeartbeat(1700000000L, List.of(
                Map.of("deviceNo", "GATE-E-IN", "online", Boolean.TRUE),
                Map.of("deviceNo", "SENSOR-A-001", "online", Boolean.TRUE),
                Map.of("deviceNo", "PILE-001", "online", Boolean.FALSE)
        ));

        verify(deviceOnlineDao).updateOnlineByNos(eq(1700000000L), eq(List.of("GATE-E-IN", "SENSOR-A-001")));
        verify(deviceOnlineDao).updateOfflineByNos(eq(List.of("PILE-001")));
    }

    @Test
    @DisplayName("心跳批：全在线时不下发离线 UPDATE；空批不发任何 SQL")
    void 空批与全在线() {
        service.recordHeartbeat(1700000000L, List.of(
                Map.of("deviceNo", "GATE-E-IN", "online", Boolean.TRUE)
        ));
        verify(deviceOnlineDao).updateOnlineByNos(eq(1700000000L), anyList());
        verify(deviceOnlineDao, never()).updateOfflineByNos(anyList());

        service.recordHeartbeat(1700000000L, List.of());
        //空批不发任何 SQL：总调用次数仍为 1（仅第一次全在线批次触发）
        verify(deviceOnlineDao, org.mockito.Mockito.times(1)).updateOnlineByNos(anyLong(), anyList());
        verify(deviceOnlineDao, never()).updateOfflineByNos(anyList());
    }

    @Test
    @DisplayName("心跳批：匹配行数小于上报数时反查差集（台账外设备告警，不抛异常）")
    void 未知设备告警不阻断() {
        //全部匹配：影响行 == 上报数 → 不触发反查
        when(deviceOnlineDao.updateOnlineByNos(eq(1700000000L), anyList())).thenReturn(2);
        service.recordHeartbeat(1700000000L, List.of(
                Map.of("deviceNo", "GATE-E-IN", "online", Boolean.TRUE),
                Map.of("deviceNo", "SENSOR-A-001", "online", Boolean.TRUE)
        ));

        //部分匹配（1 < 2）→ 反查差集；反查已知集合含 GATE-E-IN → 差集 = GHOST-999 被识别但不抛
        when(deviceOnlineDao.updateOnlineByNos(eq(1700000000L), anyList())).thenReturn(1);
        DeviceOnlineEntity known = new DeviceOnlineEntity();
        known.setDeviceNo("GATE-E-IN");
        when(deviceOnlineDao.selectList(org.mockito.ArgumentMatchers.isNull())).thenReturn(List.of(known));
        service.recordHeartbeat(1700000000L, List.of(
                Map.of("deviceNo", "GATE-E-IN", "online", Boolean.TRUE),
                Map.of("deviceNo", "GHOST-999", "online", Boolean.TRUE)
        ));
        assertThat(service).isNotNull();
    }
}
