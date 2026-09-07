package com.reason.barrier.registry;

import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.Barrier;
import com.reason.barrier.reporter.EventReporter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 设备注册表（本进程"安装"了哪些物理杆）
 *
 * <p>由 sim.devices 配置构建：设备上电即存在（无 DB，进程重启回到初始态——真实设备语义）。
 * 设备编号与平台台账对齐：平台登记档案 + 模拟器安装实物，两者靠 deviceNo 契约握手。</p>
 */
@Slf4j
@Component
public class BarrierRegistry {

    private final SimProperties properties;
    private final EventReporter reporter;
    private final Map<String, Barrier> barriers = new LinkedHashMap<>();

    /**
     * 本进程（=这批安装的设备）的启动代际（协议 v2 bootId）：sim 进程重启 = 设备断电重启——
     * bootId 变化让平台重置事件序基线（重启后从 1 重计的事件不被旧序守卫误拒）
     */
    private final String bootId = UUID.randomUUID().toString().replace("-", "");

    public BarrierRegistry(SimProperties properties, EventReporter reporter) {
        this.properties = properties;
        this.reporter = reporter;
    }

    @PostConstruct
    public void init() {
        for (SimProperties.DeviceCfg cfg : properties.getDevices()) {
            barriers.put(cfg.getDeviceNo(),
                    new Barrier(cfg.getDeviceNo(), cfg.getName(), properties.getMoveMillis(), reporter, bootId));
        }
        log.info("设备注册完成：{} 台，bootId={}（重启代际——平台事件序守卫依据）",
                barriers.size(), bootId);
    }

    /**
     * 按编号取设备
     *
     * @return 设备实例；未安装返回 null（平台向不存在的设备发指令=设备不存在）
     */
    public Barrier get(String deviceNo) {
        return barriers.get(deviceNo);
    }

    /**
     * 本进程安装的全部设备（心跳周期上报/状态快照用；不可变视图防外部篡改注册表）
     */
    public Collection<Barrier> all() {
        return Collections.unmodifiableCollection(barriers.values());
    }

    public int size() {
        return barriers.size();
    }
}
