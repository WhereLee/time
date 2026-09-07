package com.reason.modules.device.config;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 设备通道 HMAC 签名（0.5 per-device 凭证，协议 v2 §3.1 签名域）
 *
 * <p>平台与设备共享同一算法（双端各自实现，契约测试钉一致）：
 * HMAC-SHA256(secret, canonical)，hex 输出。canonical 规范化串 = 字段按协议序以 '|' 拼接（null 为空串）：
 * 事件：deviceNo|state|commandSeq|bootId|eventSeq；心跳：deviceNo|state；下行指令：deviceNo|action|commandSeq。
 * 比对用常量时间 MessageDigest.isEqual（防时序侧信道）。
 * 防重放分工：事件重放由台账序守卫拒（0.1），心跳重放幂等无害（仅刷新 TTL）——HMAC 解决"伪造"，序解决"重放"。</p>
 */
@Slf4j
public final class DeviceSignature {

    private static final String HMAC_ALGO = "HmacSHA256";

    private DeviceSignature() {
    }

    /**
     * 计算签名（hex）
     */
    public static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    /**
     * 常量时间校验（MessageDigest.isEqual：长度与内容均不短路）
     */
    public static boolean verify(String secret, String canonical, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        String expected = sign(secret, canonical);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 事件规范化串（协议 v2 §3.1：commandSeq 可空 -> 空串）
     */
    public static String canonicalEvent(String deviceNo, Integer state, Long commandSeq, String bootId, Long eventSeq) {
        return join(deviceNo, String.valueOf(state),
                commandSeq == null ? "" : String.valueOf(commandSeq),
                bootId == null ? "" : bootId,
                eventSeq == null ? "" : String.valueOf(eventSeq));
    }

    /**
     * 心跳规范化串
     */
    public static String canonicalHeartbeat(String deviceNo, Integer state) {
        return join(deviceNo, state == null ? "" : String.valueOf(state));
    }

    /**
     * 下行指令规范化串
     */
    public static String canonicalCommand(String deviceNo, String action, long seq) {
        return join(deviceNo, action, String.valueOf(seq));
    }

    private static String join(String... parts) {
        return String.join("|", parts);
    }
}
