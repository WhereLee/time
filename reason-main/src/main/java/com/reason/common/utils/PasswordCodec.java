package com.reason.common.utils;

import org.apache.shiro.crypto.hash.Sha256Hash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码编解码器：BCrypt 为主，兼容存量 SHA-256(salt) 哈希的渐进迁移
 *
 * <p>背景：历史密码为单轮 SHA-256 + 盐，抗 GPU 暴力破解能力弱；
 * 迁移策略（渐进式）：验证时按哈希格式分流——BCrypt 格式直接校验；
 * 遗留格式按旧算法校验，验证通过后由调用方触发重哈希升级（见登录逻辑），
 * 新密码一律 BCrypt（自带随机盐，慢哈希，可调工作因子）。</p>
 */
public class PasswordCodec {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    /** BCrypt 哈希前缀（$2a$ / $2b$ / $2y$） */
    private static final String BCRYPT_PREFIX = "$2";

    private PasswordCodec() {
    }

    /** 加密新密码（BCrypt，自带随机盐） */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码：BCrypt 格式走 BCrypt 校验；否则按遗留 SHA-256(salt) 校验
     *
     * @param rawPassword 明文密码
     * @param storedHash  库中存储的哈希
     * @param legacySalt  遗留哈希的盐值（BCrypt 格式下不使用）
     */
    public static boolean matches(String rawPassword, String storedHash, String legacySalt) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        return storedHash.startsWith(BCRYPT_PREFIX)
                ? ENCODER.matches(rawPassword, storedHash)
                : new Sha256Hash(rawPassword, legacySalt).toHex().equals(storedHash);
    }

    /** 是否遗留哈希格式（非 BCrypt），登录成功后应触发升级 */
    public static boolean isLegacy(String storedHash) {
        return storedHash != null && !storedHash.startsWith(BCRYPT_PREFIX);
    }
}
