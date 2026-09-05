package com.reason.device.controller;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.reporter.ParkingEventReporter;
import com.reason.device.sim.SimDeviceRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟控制台单元测试：充电桩事件（start/finish/cancel）与停车/充电通道区分
 *
 * <p>本类验证手控台的通道分发逻辑（桩事件走 /device/charging/*，停车设备拒绝充电事件）；
 * 双进程真实链路（sim → main）由 M1-7 联调/M1-9 云上实证。</p>
 */
@DisplayName("模拟控制台（充电桩事件）")
@ExtendWith(MockitoExtension.class)
class SimControllerTest {

    @Mock
    private SimDeviceRegistry registry;
    @Mock
    private ParkingEventReporter reporter;
    @Mock
    private DeviceSimProperties properties;
    @InjectMocks
    private SimController simController;

    private SimDevice mockDevice(String deviceNo, boolean charger) {
        SimDevice device = org.mockito.Mockito.mock(SimDevice.class);
        //lenient：拒绝路径用例（提前 return）不消费 deviceNo，成功路径依赖其值匹配上报 stub
        org.mockito.Mockito.lenient().when(device.getDeviceNo()).thenReturn(deviceNo);
        when(device.isCharger()).thenReturn(charger);
        return device;
    }

    @Test
    @DisplayName("充电桩 start：上报成功并绑定会话句柄")
    void 充电桩开始() {
        SimDevice pile = mockDevice("PILE-001", true);
        when(pile.isIdle()).thenReturn(true);
        when(registry.findByDeviceNo("PILE-001")).thenReturn(pile);
        when(reporter.reportChargeStart("PILE-001", "浙B12345")).thenReturn(100L);

        ResponseEntity<Map<String, Object>> resp = simController.event("PILE-001", "start", "浙B12345", null, null);

        assertThat(resp.getBody()).extracting(m -> m.get("sessionId")).isEqualTo(100L);
        verify(pile).bindSession(eq(100L), eq("浙B12345"), anyLong());
    }

    @Test
    @DisplayName("充电桩 finish：携带指定电量上报并清除会话")
    void 充电桩结束指定电量() {
        SimDevice pile = mockDevice("PILE-001", true);
        when(pile.isIdle()).thenReturn(false);
        when(pile.getSessionId()).thenReturn(100L);
        when(registry.findByDeviceNo("PILE-001")).thenReturn(pile);
        when(reporter.reportChargeFinish("PILE-001", 100L, 30_000L)).thenReturn(200L);

        ResponseEntity<Map<String, Object>> resp = simController.event("PILE-001", "finish", null, null, 30_000L);

        assertThat(resp.getBody()).extracting(m -> m.get("orderId")).isEqualTo(200L);
        verify(pile).clearSession();
    }

    @Test
    @DisplayName("充电桩 finish：缺省电量随机 10~60 kWh（模拟计量）")
    void 充电桩结束随机电量() {
        SimDevice pile = mockDevice("PILE-001", true);
        when(pile.isIdle()).thenReturn(false);
        when(pile.getSessionId()).thenReturn(100L);
        when(registry.findByDeviceNo("PILE-001")).thenReturn(pile);

        simController.event("PILE-001", "finish", null, null, null);

        ArgumentCaptor<Long> whCaptor = ArgumentCaptor.forClass(Long.class);
        verify(reporter).reportChargeFinish(eq("PILE-001"), eq(100L), whCaptor.capture());
        assertThat(whCaptor.getValue()).isBetween(10_000L, 60_000L);
    }

    @Test
    @DisplayName("停车设备触发 start：拒绝（400）且不上报（通道类型隔离）")
    void 停车设备拒绝充电事件() {
        SimDevice gate = mockDevice("DEV-001", false);
        when(registry.findByDeviceNo("DEV-001")).thenReturn(gate);

        ResponseEntity<Map<String, Object>> resp = simController.event("DEV-001", "start", "浙B12345", null, null);

        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
        verify(reporter, never()).reportChargeStart(anyString(), anyString());
    }

    @Test
    @DisplayName("cancel 通道分发：充电桩取消走充电通道，停车设备走停车通道")
    void 取消通道分发() {
        SimDevice pile = mockDevice("PILE-001", true);
        when(pile.isIdle()).thenReturn(false);
        when(pile.getSessionId()).thenReturn(100L);
        when(registry.findByDeviceNo("PILE-001")).thenReturn(pile);
        SimDevice gate = mockDevice("DEV-001", false);
        when(gate.isIdle()).thenReturn(false);
        when(gate.getSessionId()).thenReturn(50L);
        when(registry.findByDeviceNo("DEV-001")).thenReturn(gate);

        simController.event("PILE-001", "cancel", null, "桩中止", null);
        verify(reporter).reportChargeCancel("PILE-001", 100L, "桩中止");

        simController.event("DEV-001", "cancel", null, null, null);
        verify(reporter).reportCancel("DEV-001", 50L, "设备上报取消");
    }

    @Test
    @DisplayName("充电桩忙态 start：拒绝重复充电")
    void 充电桩忙态拒绝() {
        SimDevice pile = mockDevice("PILE-001", true);
        when(pile.isIdle()).thenReturn(false);
        when(pile.getSessionId()).thenReturn(100L);
        when(registry.findByDeviceNo("PILE-001")).thenReturn(pile);

        ResponseEntity<Map<String, Object>> resp = simController.event("PILE-001", "start", "浙B12345", null, null);

        assertThat(resp.getBody()).extracting(m -> m.get("msg")).asString().contains("忙");
        verify(reporter, never()).reportChargeStart(anyString(), anyString());
    }
}
