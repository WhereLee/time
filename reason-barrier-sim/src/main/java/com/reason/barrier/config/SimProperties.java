package com.reason.barrier.config;

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
     * 平台心跳上报地址（平台侧 /api/device/heartbeat）
     */
    private String heartbeatUrl;

    /**
     * 心跳间隔秒数（需小于平台侧心跳超时阈值 reason.barrier.heartbeat-timeout-seconds，
     * 留足网络抖动余量：阈值 30s 对应间隔 10s，连续丢 2 次心跳才判离线）
     */
    private int heartbeatIntervalSeconds = 10;

    /**
     * 升降动作耗时毫秒（模拟机械动作非瞬时）
     */
    private long moveMillis = 3000;

    /**
     * 安装的设备清单（与平台台账 deviceNo 对齐；secret 为 0.5 per-device HMAC 密钥——
     * 环境变量注入（仓库零明文），须与平台 device_record.device_secret 同值）
     */
    private List<DeviceCfg> devices = new ArrayList<>();

    /**
     * 按设备号取 HMAC 密钥（null=未配置——fail secure：验签必然失败）
     */
    public String secretOf(String deviceNo) {
        for (DeviceCfg cfg : devices) {
            if (cfg.getDeviceNo().equals(deviceNo)) {
                return cfg.getSecret();
            }
        }
        return null;
    }

    @Data
    public static class DeviceCfg {
        private String deviceNo;
        private String name;
        /** HMAC 密钥（${ENV:} 环境变量注入——仓库零明文） */
        private String secret;
    }
}
