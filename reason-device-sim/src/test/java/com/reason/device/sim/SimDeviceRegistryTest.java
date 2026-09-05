package com.reason.device.sim;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.model.SimDeviceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备注册表测试（A 块规模化：手写清单 + 区段展开合并）
 */
@DisplayName("设备注册表（手写 + 区段展开）")
class SimDeviceRegistryTest {

    private DeviceSimProperties newProperties() {
        DeviceSimProperties props = new DeviceSimProperties();
        DeviceSimProperties.DeviceCfg dev = new DeviceSimProperties.DeviceCfg();
        dev.setDeviceNo("DEV-001");
        dev.setSpaceNo("A-001");
        dev.setDeviceType(SimDeviceType.PARK_DEVICE);
        props.getDevices().add(dev);

        DeviceSimProperties.SectionCfg sensors = new DeviceSimProperties.SectionCfg();
        sensors.setDeviceType(SimDeviceType.SENSOR);
        sensors.setNoPrefix("SENSOR-A-");
        sensors.setArea("A");
        sensors.setFrom(1);
        sensors.setTo(3);
        props.getSections().add(sensors);

        DeviceSimProperties.SectionCfg piles = new DeviceSimProperties.SectionCfg();
        piles.setDeviceType(SimDeviceType.CHARGING_PILE);
        piles.setNoPrefix("PILE-");
        piles.setArea("C");
        piles.setFrom(1);
        piles.setTo(2);
        props.getSections().add(piles);
        return props;
    }

    @Test
    @DisplayName("区段展开：位检号与车位号同构补零、桩绑 C 区；手写与区段合并计数")
    void 区段展开与合并() {
        SimDeviceRegistry registry = new SimDeviceRegistry(newProperties());

        assertThat(registry.all()).hasSize(1 + 3 + 2);

        SimDevice sensor = registry.findByDeviceNo("SENSOR-A-002");
        assertThat(sensor).isNotNull();
        assertThat(sensor.getSpaceNo()).isEqualTo("A-002");
        assertThat(sensor.getDeviceType()).isEqualTo(SimDeviceType.SENSOR);
        assertThat(sensor.isCharger()).isFalse();

        SimDevice pile = registry.findByDeviceNo("PILE-002");
        assertThat(pile).isNotNull();
        assertThat(pile.getSpaceNo()).isEqualTo("C-002");
        assertThat(pile.getDeviceType()).isEqualTo(SimDeviceType.CHARGING_PILE);
        assertThat(pile.isCharger()).isTrue();

        //边界：超出区段不存在（1..3 无 004）
        assertThat(registry.findByDeviceNo("SENSOR-A-004")).isNull();
        assertThat(registry.findByDeviceNo("PILE-003")).isNull();
    }

    @Test
    @DisplayName("空配置：注册表为空（上下文启动/测试兜底）")
    void 空配置() {
        SimDeviceRegistry registry = new SimDeviceRegistry(new DeviceSimProperties());
        assertThat(registry.all()).isEmpty();
    }
}
