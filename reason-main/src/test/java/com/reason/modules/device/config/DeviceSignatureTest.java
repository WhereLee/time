package com.reason.modules.device.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备 HMAC 签名工具测试（0.5 per-device 凭证）：
 * 签名稳定可复现（与 sim 侧实现同算法同规范，端到端剧本钉双端一致）、篡改/缺失被拒、常量时间校验
 */
@DisplayName("设备HMAC签名")
class DeviceSignatureTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("同 canonical 签名稳定可复现（双端联调的前提）")
    void 签名可复现() {
        String canonical = DeviceSignature.canonicalEvent("BARRIER-E-01", 1, 7L, "boot1", 5L);
        String s1 = DeviceSignature.sign(SECRET, canonical);
        String s2 = DeviceSignature.sign(SECRET, canonical);
        assertThat(s1).isEqualTo(s2).hasSize(64); //SHA-256 hex 64 位
        assertThat(DeviceSignature.verify(SECRET, canonical, s1)).isTrue();
    }

    @Test
    @DisplayName("篡改任一字段/密钥/signature 均验签失败")
    void 篡改被拒() {
        String canonical = DeviceSignature.canonicalEvent("BARRIER-E-01", 1, 7L, "boot1", 5L);
        String sig = DeviceSignature.sign(SECRET, canonical);
        assertThat(DeviceSignature.verify("wrong-secret", canonical, sig)).isFalse();
        assertThat(DeviceSignature.verify(SECRET, DeviceSignature.canonicalEvent("BARRIER-E-01", 2, 7L, "boot1", 5L), sig)).isFalse();
        assertThat(DeviceSignature.verify(SECRET, canonical, sig + "0")).isFalse();
        assertThat(DeviceSignature.verify(SECRET, canonical, null)).isFalse();
        assertThat(DeviceSignature.verify(SECRET, canonical, "")).isFalse();
    }

    @Test
    @DisplayName("commandSeq=null 与心跳 canonical 拼接规范（空串占位）")
    void canonical规范() {
        //外力改态事件：commandSeq 空 -> 空串占位
        assertThat(DeviceSignature.canonicalEvent("BARRIER-E-01", 2, null, "boot1", 6L))
                .isEqualTo("BARRIER-E-01|2||boot1|6");
        assertThat(DeviceSignature.canonicalHeartbeat("BARRIER-E-01", 2))
                .isEqualTo("BARRIER-E-01|2");
        assertThat(DeviceSignature.canonicalCommand("BARRIER-E-01", "OPEN", 9))
                .isEqualTo("BARRIER-E-01|OPEN|9");
    }
}
