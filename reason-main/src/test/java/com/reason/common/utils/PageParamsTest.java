package com.reason.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页参数钳制（T15）：非法输入不 500（按默认钳制）、越界不放行（limit 截断 200）——
 * 管理端查询参数防 DoS 语义的最小集
 */
class PageParamsTest {

    @Test
    @DisplayName("正常值原样通过")
    void normal() {
        assertThat(PageParams.page("3")).isEqualTo(3);
        assertThat(PageParams.limit("20")).isEqualTo(20);
    }

    @Test
    @DisplayName("缺省与空白按默认值")
    void blank_usesDefault() {
        assertThat(PageParams.page(null)).isEqualTo(1);
        assertThat(PageParams.limit(null)).isEqualTo(10);
        assertThat(PageParams.page("  ")).isEqualTo(1);
        assertThat(PageParams.limit("")).isEqualTo(10);
    }

    @Test
    @DisplayName("非数字输入按默认钳制——不再 500（低权用户垃圾参数 DoS 面关闭）")
    void nonNumeric_clampedToDefault() {
        assertThat(PageParams.page("abc")).isEqualTo(1);
        assertThat(PageParams.page("-x-")).isEqualTo(1);
        assertThat(PageParams.limit("NaN")).isEqualTo(10);
    }

    @Test
    @DisplayName("越界值截断：page 下限 1、limit 夹在 [1,200]")
    void outOfRange_clamped() {
        assertThat(PageParams.page("0")).isEqualTo(1);
        assertThat(PageParams.page("-5")).isEqualTo(1);
        assertThat(PageParams.limit("0")).isEqualTo(1);
        assertThat(PageParams.limit("99999")).isEqualTo(PageParams.MAX_LIMIT);
        assertThat(PageParams.limit("-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("超大数字（溢出 int）按默认钳制——不抛 NumberFormatException")
    void overflow_clampedToDefault() {
        assertThat(PageParams.page("99999999999999999999")).isEqualTo(1);
        assertThat(PageParams.limit("2147483648")).isEqualTo(10);
    }
}
