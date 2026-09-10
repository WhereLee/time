package com.reason.modules.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上行入口限流配置（reason.device.traffic.*，批次5 横切治理）
 */
@Data
@Component
@ConfigurationProperties(prefix = "reason.device.traffic")
public class DeviceTrafficProperties {

    /** 总开关（false=不限流；默认开） */
    private boolean enabled = true;

    /** per-device 令牌桶速率（令牌/秒）——正常心跳约 0.1rps/台，5rps 只有"发疯设备"才触顶 */
    private int deviceRps = 5;

    /** per-device 桶容量（突发余量） */
    private int deviceBurst = 10;

    /** 全局水位闸速率（令牌/秒）——50 台正常上行合计约 5-6rps，100rps 留足回归余量 */
    private int globalRps = 100;

    /** 全局水位桶容量 */
    private int globalBurst = 200;
}
