package com.reason.modules.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备通道配置（reason.device.*——0.5 per-device HMAC 后仅剩通道寻址）
 *
 * <p>simBaseUrl：设备模拟服务地址（本地双进程联调；接真实设备时此处即设备协议网关地址）。
 * 鉴权已从共享口令（X-Device-Token）升级为 per-device HMAC（DeviceSignature +
 * DeviceChannelAuthenticator，0.5）：每台设备独立密钥，平台按 X-Device-No 查库验签——
 * 这里不再持有任何共享凭证（仓库零明文）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "reason.device")
public class DeviceChannelProperties {

    /**
     * 设备模拟服务地址（指令下发目标）
     */
    private String simBaseUrl;
}
