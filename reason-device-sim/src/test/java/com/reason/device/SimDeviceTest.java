package com.reason.device;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.model.SimDeviceType;
import com.reason.device.sim.Plates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模拟设备运行态测试（纯内存状态机：空闲 ⇄ 会话中；在线态/类型语义）
 */
@DisplayName("模拟设备状态")
class SimDeviceTest {

    private SimDevice newDevice() {
        DeviceSimProperties.DeviceCfg cfg = new DeviceSimProperties.DeviceCfg();
        cfg.setDeviceNo("DEV-001");
        cfg.setSpaceNo("A-001");
        cfg.setDeviceType(SimDeviceType.PARK_DEVICE);
        return new SimDevice(cfg);
    }

    @Test
    @DisplayName("初始空闲；入场上报绑定会话后占用；出场清除后回到空闲")
    void 会话绑定与清除() {
        SimDevice device = newDevice();
        assertThat(device.isIdle()).isTrue();

        device.bindSession(100L, "浙B8K521", 1700000000L);
        assertThat(device.isIdle()).isFalse();
        assertThat(device.getSessionId()).isEqualTo(100L);
        assertThat(device.getPlateNo()).isEqualTo("浙B8K521");

        device.clearSession();
        assertThat(device.isIdle()).isTrue();
        assertThat(device.getSessionId()).isNull();
    }

    @Test
    @DisplayName("类型语义：充电桩走充电通道；停车演示设备为剧本唯一驱动对象；无类型设备都不属于")
    void 类型语义() {
        SimDevice pile = new SimDevice("PILE-001", "C-001", SimDeviceType.CHARGING_PILE);
        assertThat(pile.isCharger()).isTrue();
        assertThat(pile.isParkDevice()).isFalse();

        SimDevice park = newDevice();
        assertThat(park.isParkDevice()).isTrue();
        assertThat(park.isCharger()).isFalse();

        SimDevice sensor = new SimDevice("SENSOR-A-001", "A-001", SimDeviceType.SENSOR);
        assertThat(sensor.isCharger()).isFalse();
        assertThat(sensor.isParkDevice()).isFalse();
    }

    @Test
    @DisplayName("在线态：默认在线；故障注入置离线后可恢复")
    void 在线态切换() {
        SimDevice device = newDevice();
        assertThat(device.isOnline()).isTrue();
        device.setOnline(false);
        assertThat(device.isOnline()).isFalse();
        device.setOnline(true);
        assertThat(device.isOnline()).isTrue();
    }

    @Test
    @DisplayName("车牌池轮转稳定：同秒同牌、不同秒不同牌（确定性复现）")
    void 车牌轮转() {
        String p1 = Plates.pick(1000L);
        String p2 = Plates.pick(1000L);
        String p3 = Plates.pick(1001L);
        assertThat(p1).isEqualTo(p2);
        assertThat(p1).isNotEqualTo(p3).isNotBlank();
    }
}
