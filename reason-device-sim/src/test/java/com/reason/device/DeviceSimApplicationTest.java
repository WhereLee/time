package com.reason.device;

import com.reason.device.config.DeviceSimProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备模拟服务上下文冒烟：配置绑定 + 设备注册表可查
 */
@SpringBootTest(properties = {
        "reason.device-sim.access-token=sim-token-test",
        "reason.device-sim.parking-api-base-url=http://127.0.0.1:8200/api",
        "reason.device-sim.devices[0].deviceNo=DEV-001",
        "reason.device-sim.devices[0].spaceNo=A-001",
        "reason.device-sim.auto.enable=false"
})
@DisplayName("设备模拟服务上下文")
class DeviceSimApplicationTest {

    @Autowired
    private DeviceSimProperties properties;

    @Autowired
    private com.reason.device.sim.SimDeviceRegistry registry;

    @Test
    @DisplayName("配置绑定与注册表构建正确")
    void 配置与注册表() {
        assertThat(properties.getAccessToken()).isEqualTo("sim-token-test");
        assertThat(properties.getParkingApiBaseUrl()).isEqualTo("http://127.0.0.1:8200/api");
        assertThat(registry.findByDeviceNo("DEV-001")).isNotNull();
        assertThat(registry.findByDeviceNo("DEV-001").getSpaceNo()).isEqualTo("A-001");
        //devices 由测试 properties 整体替换（1 台）；sections 由 yml 提供（330 台展开）——只验证可查与存在，不锁计数
        assertThat(registry.all()).isNotEmpty();
    }
}
