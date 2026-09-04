package com.reason.modules.parking.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FeeCalculator 纯函数测试：取整边界矩阵（整点/跨小时/零时长/防溢出）
 *
 * <p>计费规则边界全部在此固化——未来规则引擎演进（M2）时本类断言是"语义不漂移"的基线。</p>
 */
@DisplayName("计费计算器")
class FeeCalculatorTest {

    private static final int PRICE_FEN = 200;   // 2 元/小时

    @Test
    @DisplayName("时长分钟：不足 1 分钟按 1 分钟向上取整")
    void 时长分钟向上取整() {
        assertThat(FeeCalculator.calcDurationMinutes(0)).isEqualTo(0);
        assertThat(FeeCalculator.calcDurationMinutes(1)).isEqualTo(1);
        assertThat(FeeCalculator.calcDurationMinutes(59)).isEqualTo(1);
        assertThat(FeeCalculator.calcDurationMinutes(60)).isEqualTo(1);
        assertThat(FeeCalculator.calcDurationMinutes(61)).isEqualTo(2);
        assertThat(FeeCalculator.calcDurationMinutes(3599)).isEqualTo(60);
        assertThat(FeeCalculator.calcDurationMinutes(3600)).isEqualTo(60);
        assertThat(FeeCalculator.calcDurationMinutes(3601)).isEqualTo(61);
    }

    @Test
    @DisplayName("金额：按小时向上取整（整点精确、跨小时进位、零时长 0 元）")
    void 金额按小时向上取整() {
        //0 秒 → 0 元（防御：同秒出入场不崩）
        assertThat(FeeCalculator.calcAmountFen(0, PRICE_FEN)).isEqualTo(0L);
        //不足 1 小时（59 分 59 秒）→ 1 小时
        assertThat(FeeCalculator.calcAmountFen(3599, PRICE_FEN)).isEqualTo(200L);
        //整点 1 小时 → 1 小时
        assertThat(FeeCalculator.calcAmountFen(3600, PRICE_FEN)).isEqualTo(200L);
        //跨小时（1 小时 1 秒）→ 2 小时
        assertThat(FeeCalculator.calcAmountFen(3601, PRICE_FEN)).isEqualTo(400L);
        assertThat(FeeCalculator.calcAmountFen(7200, PRICE_FEN)).isEqualTo(400L);
        assertThat(FeeCalculator.calcAmountFen(7201, PRICE_FEN)).isEqualTo(600L);
    }

    @Test
    @DisplayName("金额：单价 0（免费规则）→ 任意时长 0 元")
    void 单价为零免费() {
        assertThat(FeeCalculator.calcAmountFen(72000, 0)).isEqualTo(0L);
    }

    @Test
    @DisplayName("大时长防溢出：10 年时长（约 87600 小时）× 200 分 不溢出且精确")
    void 大时长防溢出() {
        long tenYearsSeconds = 10L * 365 * 24 * 3600;
        assertThat(FeeCalculator.calcDurationMinutes(tenYearsSeconds)).isEqualTo(5_256_000);
        assertThat(FeeCalculator.calcAmountFen(tenYearsSeconds, PRICE_FEN)).isEqualTo(17_520_000L);
    }
}
