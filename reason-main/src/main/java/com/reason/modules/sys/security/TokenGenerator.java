/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.security;

import java.security.SecureRandom;

/**
 * 生成token
 *
 * @author Mark sunlightcs@gmail.com
 */
public class TokenGenerator {

    /** 安全随机源：token 要求不可预测，禁用 MD5(UUID) 这类基于可预测输入的生成方式 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final char[] hexCode = "0123456789abcdef".toCharArray();

    public static String toHexString(byte[] data) {
        if(data == null) {
            return null;
        }
        StringBuilder r = new StringBuilder(data.length*2);
        for ( byte b : data) {
            r.append(hexCode[(b >> 4) & 0xF]);
            r.append(hexCode[(b & 0xF)]);
        }
        return r.toString();
    }

    /**
     * 生成 128 位安全随机 token（32 位十六进制，与原 MD5 输出等长，兼容 DB 列宽）
     */
    public static String generateValue() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return toHexString(bytes);
    }
}
