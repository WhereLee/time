package com.reason.sim.registry;

import com.reason.sim.config.SimProperties;
import com.reason.sim.model.Barrier;
import com.reason.sim.reporter.EventReporter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备注册表（本进程"安装"了哪些物理杆）
 *
 * <p>由 sim.devices 配置构建：设备上电即存在（无 DB，进程重启回到初始态——真实设备语义）。
 * 设备编号与平台台账对齐：平台登记档案 + 模拟器安装实物，两者靠 deviceNo 契约握手。</p>
 */
@Component
public class BarrierRegistry {

    private final SimProperties properties;
    private final EventReporter reporter;
    private final Map<String, Barrier> barriers = new LinkedHashMap<>();

    public BarrierRegistry(SimProperties properties, EventReporter reporter) {
        this.properties = properties;
        this.reporter = reporter;
    }

    @PostConstruct
    public void init() {
        for (SimProperties.DeviceCfg cfg : properties.getDevices()) {
            barriers.put(cfg.getDeviceNo(),
                    new Barrier(cfg.getDeviceNo(), cfg.getName(), properties.getMoveMillis(), reporter));
        }
    }

    /**
     * 按编号取设备
     *
     * @return 设备实例；未安装返回 null（平台向不存在的设备发指令=设备不存在）
     */
    public Barrier get(String deviceNo) {
        return barriers.get(deviceNo);
    }

    public int size() {
        return barriers.size();
    }
}
