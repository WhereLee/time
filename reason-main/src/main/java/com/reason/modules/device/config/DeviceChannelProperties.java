package com.reason.modules.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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

    /**
     * 设备令牌校验（常量时间比较：String.equals 短路特性理论上有时序侧信道，
     * 鉴权比对统一收口在此——事件/心跳两通道共用同一凭证体系）
     *
     * @param token 设备侧携带的 X-Device-Token
     * @return 令牌是否有效
     */
    public boolean matches(String token) {
        if (accessToken == null || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                accessToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
