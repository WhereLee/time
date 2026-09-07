package com.reason.modules.device.service;

import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.enums.DeviceState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 时间规则测试（"规则=判断者"的纯判断行为）：
 * 当日窗口边界（整点半开区间）+ 跨午夜窗口 + 非法配置 fail-fast
 */
@DisplayName("升降杆时间规则")
class BarrierTimeRuleTest {

    private BarrierTimeRule rule(String open, String close) {
        BarrierProperties properties = new BarrierProperties();
        properties.setAutoOpenTime(open);
        properties.setAutoCloseTime(close);
        return new BarrierTimeRule(properties);
    }

    @Test
    @DisplayName("当日窗口 08:00-18:00：左闭右开，整点即切换")
    void 当日窗口_整点边界() {
        BarrierTimeRule rule = rule("08:00", "18:00");

        //窗口前：应然降下（禁行）
        assertThat(rule.desiredState(LocalTime.of(0, 0))).isEqualTo(DeviceState.DOWN.getCode());
        assertThat(rule.desiredState(LocalTime.of(7, 59))).isEqualTo(DeviceState.DOWN.getCode());
        //左边界：08:00 整点即升（半开区间 [open, ...)，无"整点刹那模糊态"）
        assertThat(rule.desiredState(LocalTime.of(8, 0))).isEqualTo(DeviceState.UP.getCode());
        //窗口内：应然升起（放行）
        assertThat(rule.desiredState(LocalTime.of(12, 0))).isEqualTo(DeviceState.UP.getCode());
        assertThat(rule.desiredState(LocalTime.of(17, 59))).isEqualTo(DeviceState.UP.getCode());
        //右边界：18:00 整点即降（... close) 右开）
        assertThat(rule.desiredState(LocalTime.of(18, 0))).isEqualTo(DeviceState.DOWN.getCode());
        assertThat(rule.desiredState(LocalTime.of(23, 59))).isEqualTo(DeviceState.DOWN.getCode());
    }

    @Test
    @DisplayName("跨午夜窗口 22:00-06:00：夜间放行语义正确")
    void 跨午夜窗口() {
        BarrierTimeRule rule = rule("22:00", "06:00");

        //窗口前
        assertThat(rule.desiredState(LocalTime.of(21, 59))).isEqualTo(DeviceState.DOWN.getCode());
        //左边界：22:00 整点即升
        assertThat(rule.desiredState(LocalTime.of(22, 0))).isEqualTo(DeviceState.UP.getCode());
        //跨午夜段：深夜与凌晨都在窗口内
        assertThat(rule.desiredState(LocalTime.of(23, 59))).isEqualTo(DeviceState.UP.getCode());
        assertThat(rule.desiredState(LocalTime.of(0, 0))).isEqualTo(DeviceState.UP.getCode());
        assertThat(rule.desiredState(LocalTime.of(5, 59))).isEqualTo(DeviceState.UP.getCode());
        //右边界：06:00 整点即降
        assertThat(rule.desiredState(LocalTime.of(6, 0))).isEqualTo(DeviceState.DOWN.getCode());
        assertThat(rule.desiredState(LocalTime.of(12, 0))).isEqualTo(DeviceState.DOWN.getCode());
    }

    @Test
    @DisplayName("非法时间配置：启动校验 fail-fast，不留到任务执行才发现")
    void 非法配置_failFast() {
        BarrierTimeRule rule = rule("25:00", "18:00");
        assertThatThrownBy(rule::validate)
                .isInstanceOf(DateTimeParseException.class);
    }
}
