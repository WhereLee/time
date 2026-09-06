package com.reason.modules.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备通道配置（reason.device.*）
 *
 * <p>accessToken：设备通道共享口令（/device/event 由模拟器携 X-Device-Token 调用；
 * 样例级固定口令，生产形态为独立设备鉴权体系/令牌注册制——当前为本地联调简化）。
 * simBaseUrl：设备模拟服务地址（本地双进程联调；接真实设备时此处即设备协议网关地址）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "reason.device")
public class DeviceChannelProperties {

    /**
     * 设备通道访问令牌（须与模拟器侧一致）
     */
    private String accessToken;

    /**
     * 设备模拟服务地址（指令下发目标）
     */
    private String simBaseUrl;
}
