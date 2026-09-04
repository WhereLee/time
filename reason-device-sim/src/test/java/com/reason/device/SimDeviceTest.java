package com.reason.device;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.sim.Plates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模拟设备运行态测试（纯内存状态机：idle ↔ 会话中）
 */
@DisplayName("模拟设备状态")
class SimDeviceTest {

    private SimDevice newDevice() {
        DeviceSimProperties.DeviceCfg cfg = new DeviceSimProperties.DeviceCfg();
        cfg.setDeviceNo("DEV-001");
        cfg.setSpaceNo("A-001");
        cfg.setDeviceType("地锁型");
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
    @DisplayName("车牌池轮转稳定：同秒同牌、不同秒不同牌（确定性复现）")
    void 车牌轮转() {
        String p1 = Plates.pick(1000L);
        String p2 = Plates.pick(1000L);
        String p3 = Plates.pick(1001L);
        assertThat(p1).isEqualTo(p2);
        assertThat(p1).isNotEqualTo(p3).isNotBlank();
    }
}
