package com.reason.device.sim;

import com.reason.device.config.DeviceSimProperties;
import com.reason.device.model.SimDevice;
import com.reason.device.model.SimDeviceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟设备注册表：手写清单 + 批量区段 → 运行态设备（应用启动时构建一次）
 *
 * <p>A 块规模化：sections 按车位区段展开位检/充电桩（与 db/08 资产同构），
 * 设备总数 = 手写（闸机 6 + 停车演示 3）+ 区段（位检 300 + 桩 30）= 339。</p>
 */
@Slf4j
@Component
public class SimDeviceRegistry {

    private final Map<String, SimDevice> devices = new LinkedHashMap<>();

    public SimDeviceRegistry(DeviceSimProperties properties) {
        for (DeviceSimProperties.DeviceCfg cfg : properties.getDevices()) {
            SimDevice device = new SimDevice(cfg);
            put(device);
        }
        for (DeviceSimProperties.SectionCfg section : properties.getSections()) {
            SimDeviceType type = section.getDeviceType();
            if (type == null) {
                log.warn("设备区段缺少 deviceType，跳过：noPrefix={}", section.getNoPrefix());
                continue;
            }
            for (int n = section.getFrom(); n <= section.getTo(); n++) {
                String deviceNo = section.getNoPrefix() + pad(n, section.getNoPad());
                String spaceNo = section.getArea() + "-" + pad(n, 3);
                put(new SimDevice(deviceNo, spaceNo, type));
            }
        }
        log.info("设备注册完成：共 {} 台（手写 {} + 区段展开 {}）",
                devices.size(), properties.getDevices().size(), devices.size() - properties.getDevices().size());
    }

    /** 注册（重复 deviceNo 覆盖并告警：配置漂移保护） */
    private void put(SimDevice device) {
        SimDevice existed = devices.put(device.getDeviceNo(), device);
        if (existed != null) {
            log.warn("设备编号重复，后配置覆盖：{}（{}）", device.getDeviceNo(), existed.getDeviceType());
        }
    }

    private static String pad(int n, int width) {
        return String.format("%0" + width + "d", n);
    }

    public SimDevice findByDeviceNo(String deviceNo) {
        return devices.get(deviceNo);
    }

    public List<SimDevice> all() {
        return new ArrayList<>(devices.values());
    }
}
