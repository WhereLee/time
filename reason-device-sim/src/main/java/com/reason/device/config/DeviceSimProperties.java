package com.reason.device.config;

import com.reason.device.model.SimDeviceType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备模拟配置（application.yml reason.device-sim.*）
 *
 * <p>A 块规模化：手写 devices（演示/闸机）+ sections 区段展开（位检/充电桩批量，与 db/08 资产同构），
 * 启动时由 {@code SimDeviceRegistry} 合并为完整设备清单。</p>
 */
@Data
@ConfigurationProperties(prefix = "reason.device-sim")
public class DeviceSimProperties {

    /** 设备通道密钥（须与 reason-main reason.device.access-token 一致） */
    private String accessToken;
    /** reason-main 设备接入服务地址（如 http://127.0.0.1:8200/api） */
    private String parkingApiBaseUrl;
    /** 手写设备清单（闸机/停车演示设备等无法规则展开的设备） */
    private List<DeviceCfg> devices = new ArrayList<>();
    /** 批量区段（按车位区段展开位检/充电桩） */
    private List<SectionCfg> sections = new ArrayList<>();
    /** 心跳上报配置 */
    private HeartbeatCfg heartbeat = new HeartbeatCfg();
    /** 自动剧本配置 */
    private AutoCfg auto = new AutoCfg();

    /** 单台模拟设备静态配置 */
    @Data
    public static class DeviceCfg {
        /** 设备编号（如 GATE-E-IN/DEV-001） */
        private String deviceNo;
        /** 绑定车位编号（闸机等无车位设备留空） */
        private String spaceNo;
        /** 设备类型（SimDeviceType 枚举名） */
        private SimDeviceType deviceType;
    }

    /**
     * 批量展开段：deviceNo = noPrefix + n 左补零；spaceNo = area + '-' + n 左补零 3 位
     * （与 db/08 资产编号规则同构：SENSOR-A-001↔A-001、PILE-001↔C-001）
     */
    @Data
    public static class SectionCfg {
        /** 设备类型：SENSOR / CHARGING_PILE（闸机无区段规则走手写） */
        private SimDeviceType deviceType;
        /** 设备号前缀（如 SENSOR-A-、PILE-） */
        private String noPrefix;
        /** 绑定车位区（A/B/C） */
        private String area;
        /** 区段起点（含） */
        private int from;
        /** 区段终点（含） */
        private int to;
        /** 设备号序号补零位数（默认 3） */
        private int noPad = 3;
    }

    /** 心跳上报（批量聚合，模拟网关语义） */
    @Data
    public static class HeartbeatCfg {
        /** 是否启用（main 端 /device/heartbeat 就绪后开启） */
        private boolean enabled = false;
        /** 心跳周期秒数 */
        private long intervalSeconds = 10;
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
