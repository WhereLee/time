package com.reason.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PasswordCodec 单元测试（纯函数，无 mock）
 *
 * <p>遗留向量来自与 Shiro 2.0.0 Sha256Hash 的位级对比取证（2026-09-04），
 * 该断言固化了「手写 SHA-256(salt||password) 与 Shiro SimpleHash 位级兼容」这一结论。</p>
 */
@DisplayName("密码编解码器")
class PasswordCodecTest {

    private static final String LEGACY_SALT = "CYiKIzx4410U9yaBPBHE";
    private static final String LEGACY_PWD = "admin123";
    private static final String LEGACY_HASH = "06bf8058a83e7c94b345e6eab9964956ea13ce904e7b5025e333127c24f94794";

    @Test
    @DisplayName("BCrypt 编码：产生 $2a$ 前缀哈希且盐随机（同明文两次编码结果不同）")
    void BCrypt编码_产生带前缀的哈希且盐随机() {
        String h1 = PasswordCodec.encode("admin123");
        String h2 = PasswordCodec.encode("admin123");

        assertThat(h1).startsWith("$2a$").hasSize(60);
        assertThat(h2).isNotEqualTo(h1);
    }

    @Test
    @DisplayName("BCrypt 校验：正确密码通过，错误密码拒绝")
    void BCrypt格式_正确密码校验通过_错误密码校验失败() {
        String hash = PasswordCodec.encode("admin123");

        assertThat(PasswordCodec.matches("admin123", hash, null)).isTrue();
        assertThat(PasswordCodec.matches("wrong-password", hash, null)).isFalse();
    }

    @Test
    @DisplayName("遗留 SHA-256 校验：与 Shiro 位级兼容的取证向量匹配成功")
    void 遗留SHA256格式_校验通过_与Shiro位级兼容() {
        assertThat(PasswordCodec.matches(LEGACY_PWD, LEGACY_HASH, LEGACY_SALT)).isTrue();
    }

    @Test
    @DisplayName("遗留 SHA-256 校验：密码错误时拒绝")
    void 遗留SHA256格式_密码错误_校验失败() {
        assertThat(PasswordCodec.matches("wrong-password", LEGACY_HASH, LEGACY_SALT)).isFalse();
    }

    @Test
    @DisplayName("遗留判定：按 $2 前缀分流，空值安全")
    void 遗留判定_按前缀分流且空值安全() {
        assertThat(PasswordCodec.isLegacy(LEGACY_HASH)).isTrue();
        assertThat(PasswordCodec.isLegacy(PasswordCodec.encode("admin123"))).isFalse();
        assertThat(PasswordCodec.isLegacy(null)).isFalse();
    }

    @Test
    @DisplayName("空参数防御：任一参数为空校验一律失败")
    void 空参数防御_校验一律失败() {
        assertThat(PasswordCodec.matches(null, LEGACY_HASH, LEGACY_SALT)).isFalse();
        assertThat(PasswordCodec.matches(LEGACY_PWD, null, LEGACY_SALT)).isFalse();
    }
}
