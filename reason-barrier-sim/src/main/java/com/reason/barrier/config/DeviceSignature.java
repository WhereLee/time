package com.reason.barrier.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 设备通道 HMAC 签名（0.5 per-device 凭证——与平台侧 DeviceSignature 同算法同规范，
 * 契约见 contracts/PROTOCOL-V2.md §3.1；双端实现由端到端剧本钉一致）
 *
 * <p>HMAC-SHA256(secret, canonical) hex；canonical 以 '|' 拼接（null 为空串）：
 * 事件：deviceNo|state|commandSeq|bootId|eventSeq；心跳：deviceNo|state；
 * 下行指令/查询（平台→sim，sim 验）：deviceNo|action|seq。</p>
 */
public final class DeviceSignature {

    private static final String HMAC_ALGO = "HmacSHA256";

    private DeviceSignature() {
    }

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

    public static boolean verify(String secret, String canonical, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        String expected = sign(secret, canonical);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public static String canonicalEvent(String deviceNo, Integer state, Long commandSeq, String bootId, Long eventSeq) {
        return join(deviceNo, String.valueOf(state),
                commandSeq == null ? "" : String.valueOf(commandSeq),
                bootId == null ? "" : bootId,
                eventSeq == null ? "" : String.valueOf(eventSeq));
    }

    public static String canonicalHeartbeat(String deviceNo, Integer state) {
        return join(deviceNo, state == null ? "" : String.valueOf(state));
    }

    public static String canonicalCommand(String deviceNo, String action, long seq) {
        return join(deviceNo, action, String.valueOf(seq));
    }

    private static String join(String... parts) {
        return String.join("|", parts);
    }
}
