package com.reason.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备模拟配置（application.yml reason.device-sim.*）
 */
@Data
@ConfigurationProperties(prefix = "reason.device-sim")
public class DeviceSimProperties {

    /** 设备通道密钥（须与 reason-main reason.device.access-token 一致） */
    private String accessToken;
    /** reason-main 设备接入服务地址（如 http://127.0.0.1:8200/api） */
    private String parkingApiBaseUrl;
    /** 模拟设备清单 */
    private List<DeviceCfg> devices = new ArrayList<>();
    /** 自动剧本配置 */
    private AutoCfg auto = new AutoCfg();

    /** 单台模拟设备静态配置 */
    @Data
    public static class DeviceCfg {
        /** 设备编号（如 DEV-001） */
        private String deviceNo;
        /** 绑定车位编号（如 A-001） */
        private String spaceNo;
        /** 设备类型（地锁型/升降杆型，仅展示） */
        private String deviceType;
    }

    /** 自动剧本 */
    @Data
    public static class AutoCfg {
        /** 是否启用自动剧本循环 */
        private boolean enable = false;
        /** 入场后停留秒数，到时自动出场上报 */
        private long staySeconds = 15;
        /** 剧本扫描间隔秒数 */
        private long intervalSeconds = 3;
    }
}
