package com.reason.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    private static final char[] HEX_CODE = "0123456789abcdef".toCharArray();

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
                : sha256WithSaltSuffix(rawPassword, legacySalt).equals(storedHash);
    }

    /**
     * 手写遗留算法：SHA-256(rawPassword 字节 + salt 字节)，与原 Shiro SimpleHash(SHA-256, source, salt)
     * 位级等价（按 UTF-8 字节序拼接后摘要，小写 hex），保证存量用户可验证通过
     */
    private static String sha256WithSaltSuffix(String rawPassword, String legacySalt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            //字节序与 Shiro SimpleHash 一致：先 salt 后 password（取证验证，顺序颠倒则存量用户无法登录）
            md.update(legacySalt.getBytes(StandardCharsets.UTF_8));
            md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(HEX_CODE[(b >> 4) & 0xF]);
                sb.append(HEX_CODE[b & 0xF]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /** 是否遗留哈希格式（非 BCrypt），登录成功后应触发升级 */
    public static boolean isLegacy(String storedHash) {
        return storedHash != null && !storedHash.startsWith(BCRYPT_PREFIX);
    }
}
