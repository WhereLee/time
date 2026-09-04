package com.reason.device.sim;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟设备注册表：配置清单 → 运行态设备（应用启动时构建一次）
 */
@Component
public class SimDeviceRegistry {

    private final Map<String, SimDevice> devices = new LinkedHashMap<>();

    public SimDeviceRegistry(DeviceSimProperties properties) {
        for (DeviceSimProperties.DeviceCfg cfg : properties.getDevices()) {
            devices.put(cfg.getDeviceNo(), new SimDevice(cfg));
        }
    }

    public SimDevice findByDeviceNo(String deviceNo) {
        return devices.get(deviceNo);
    }

    public List<SimDevice> all() {
        return new ArrayList<>(devices.values());
    }
}
