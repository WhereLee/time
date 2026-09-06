package com.reason.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Locale;

/**
 * 敏感数据掩码器：对 JSON 结构递归遍历，将敏感字段（密码/token/盐等）替换为掩码值。
 *
 * <p>用途：操作日志落库前脱敏（sys_log.log_params / log_return），防止敏感字段明文入库。</p>
 *
 * <p>匹配策略：字段名小写后包含敏感词根（password / pwd / token / salt）即掩码——
 * 覆盖性优先于精确性：漏网一个密码字段比误伤一个普通字段代价高得多。</p>
 */
public final class SensitiveDataMasker {

    /** 敏感词根（小写匹配）；注意：误伤面由词根长度控制，token 词根会命中 logToken 之类字段，可接受 */
    private static final String[] SENSITIVE_ROOTS = {"password", "pwd", "token", "salt"};

    public static final String MASK = "***";

    private SensitiveDataMasker() {
    }

    /** 对 JSON 字符串脱敏，返回脱敏后的 JSON 字符串 */
    public static String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        Object parsed = JSON.parse(json);
        Object masked = mask(parsed);
        return masked == null ? null : masked.toString();
    }

    /** 递归脱敏：JSONObject / JSONArray / 其他值原样返回 */
    static Object mask(Object value) {
        if (value instanceof JSONObject obj) {
            JSONObject result = new JSONObject();
            for (String key : obj.keySet()) {
                result.put(key, isSensitiveKey(key) ? MASK : mask(obj.get(key)));
            }
            return result;
        }
        if (value instanceof JSONArray arr) {
            JSONArray result = new JSONArray();
            for (Object item : arr) {
                result.add(mask(item));
            }
            return result;
        }
        return value;
    }

    static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        for (String root : SENSITIVE_ROOTS) {
            if (lower.contains(root)) {
                return true;
            }
        }
        return false;
    }
}
