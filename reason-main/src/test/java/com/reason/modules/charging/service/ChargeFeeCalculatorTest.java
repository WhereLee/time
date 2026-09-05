package com.reason.modules.charging.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChargeFeeCalculator 单元测试：两段费率四舍五入边界 + 快照恒等 + 防溢出
 */
@DisplayName("充电计费纯函数")
class ChargeFeeCalculatorTest {

    /** 预置标准费率（与 04-charging.sql 种子一致）：电费 80 fen/kWh + 服务费 40 fen/kWh */
    private static final int ELEC = 80;
    private static final int SERVICE = 40;

    @Test
    @DisplayName("0 电量 → 两段均 0（结算闭环防御）")
    void 零电量零金额() {
        ChargeFeeCalculator.ChargeFeeResult fee = ChargeFeeCalculator.calculate(0, ELEC, SERVICE);
        assertThat(fee.elecAmountFen()).isZero();
        assertThat(fee.serviceAmountFen()).isZero();
        assertThat(fee.amountFen()).isZero();
    }

    @Test
    @DisplayName("整除样例：30 kWh → 电费 2400 分 + 服务费 1200 分 = 36 元")
    void 整除样例() {
        //30000 Wh × 80 / 1000 = 2400 分（24 元）；× 40 / 1000 = 1200 分（12 元）
        ChargeFeeCalculator.ChargeFeeResult fee = ChargeFeeCalculator.calculate(30_000, ELEC, SERVICE);
        assertThat(fee.elecAmountFen()).isEqualTo(2400L);
        assertThat(fee.serviceAmountFen()).isEqualTo(1200L);
        assertThat(fee.amountFen()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("四舍五入边界：半厘进/舍各自独立（7 Wh 电费进位、服务费舍去）")
    void 取整边界独立() {
        //7 Wh：电费 7×80/1000=0.56 分 → 1（进位）；服务费 7×40/1000=0.28 分 → 0（舍去）
        //证明两段各自取整而非合并：若合并 (7×120+500)/1000=1 则服务费段被吞——本设计段级可见
        ChargeFeeCalculator.ChargeFeeResult fee = ChargeFeeCalculator.calculate(7, ELEC, SERVICE);
        assertThat(fee.elecAmountFen()).isEqualTo(1L);
        assertThat(fee.serviceAmountFen()).isZero();
        assertThat(fee.amountFen()).isEqualTo(1L);
    }

    @Test
    @DisplayName("四舍五入半界：余数恰为 500 时进位（单价 100 时 5 Wh 恰半）")
    void 半界进位() {
        //5 Wh × 100 = 500，余数恰为半界 → (500+500)/1000 = 1 进位（四舍五入语义，非截断）
        ChargeFeeCalculator.ChargeFeeResult fee = ChargeFeeCalculator.calculate(5, 100, 100);
        assertThat(fee.elecAmountFen()).isEqualTo(1L);
        assertThat(fee.serviceAmountFen()).isEqualTo(1L);
        assertThat(fee.amountFen()).isEqualTo(2L);
    }

    @Test
    @DisplayName("大电量防溢出与长整型安全（9999.999 kWh 级样例）")
    void 大数值防溢出() {
        //9,999,999 Wh（约 1 万度，远超单车单次但验证中间乘积无溢出/精度稳定）
        ChargeFeeCalculator.ChargeFeeResult fee = ChargeFeeCalculator.calculate(9_999_999, ELEC, SERVICE);
        assertThat(fee.elecAmountFen()).isEqualTo(800_000L);     //799,999,920+500 → /1000
        assertThat(fee.serviceAmountFen()).isEqualTo(400_000L);
        assertThat(fee.amountFen()).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("负数入参：数据损坏/配置错误保护，抛 IllegalArgumentException 而非静默")
    void 负数入参拒绝() {
        assertThatThrownBy(() -> ChargeFeeCalculator.calculate(-1, ELEC, SERVICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为负");
        assertThatThrownBy(() -> ChargeFeeCalculator.calculate(100, -1, SERVICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为负");
        assertThatThrownBy(() -> ChargeFeeCalculator.calculate(100, ELEC, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为负");
    }
}
