package com.reason.sim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟器配置（sim.*）
 *
 * <p>devices：本进程"安装"的物理设备清单（与平台台账编号对齐——设备上电即存在，
 * 台账由管理端登记，两边靠 deviceNo 契约对齐）；moveMillis：模拟升降耗时（真实设备
 * 升降非瞬时，到位需要时间）。</p>
 */
@Data
@ConfigurationProperties(prefix = "sim")
public class SimProperties {

    /**
     * 平台事件上报地址（平台侧 /api/device/event）
     */
    private String eventUrl;

    /**
     * 平台侧设备通道令牌（X-Device-Token，须与 reason-main 的 reason.device.access-token 一致）
     */
    private String token;

    /**
     * 升降动作耗时毫秒（模拟机械动作非瞬时）
     */
    private long moveMillis = 3000;

    /**
     * 安装的设备清单（与平台台账 deviceNo 对齐）
     */
    private List<DeviceCfg> devices = new ArrayList<>();

    @Data
    public static class DeviceCfg {
        private String deviceNo;
        private String name;
    }
}
