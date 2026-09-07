package com.reason.common.utils;

/**
 * 分页参数解析与钳制（T15：管理端分页无校验——非法输入一律 500、超大 limit 可被低权用户
 * 当 DoS 放大面；统一收口：非数字/越界按边界值钳制，绝不 500、绝不放行无界大页）
 *
 * <p>策略：page 下限 1；limit 下限 1、上限 {@link #MAX_LIMIT}（超出按上限截断——查询接口
 * 翻大页是拖库面，超过 200 条的诉求应走筛选而非翻页）。</p>
 */
public final class PageParams {

    /** 单页上限（200：足够任何管理列表一屏+滚动；超大值拒绝而非放行） */
    public static final int MAX_LIMIT = 200;

    /**
     * 解析页码（默认 1；非法值按 1 钳制）
     */
    public static int page(String raw) {
        return Math.max(parse(raw, 1), 1);
    }

    /**
     * 解析每页条数（默认 10；非法值按 10 钳制；越界截断到 [1, MAX_LIMIT]）
     */
    public static int limit(String raw) {
        int value = parse(raw, 10);
        return Math.min(MAX_LIMIT, Math.max(value, 1));
    }

    private static int parse(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            //非法输入按默认钳制：查询参数的垃圾值不该击穿到异常面（500+error 日志刷屏 = 低权 DoS）
            return defaultValue;
        }
    }

    private PageParams() {
    }
}
