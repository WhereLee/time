package com.reason.modules.charging.service;

/**
 * 充电计费纯函数（非 Spring Bean，与停车 FeeCalculator 同范式：纯整数零浮点/边界防御/可单测）
 *
 * <p>计费口径（M1-1 定稿）：电量存整数 Wh；两段单价 fen/kWh；段金额各自四舍五入到分——
 * {@code (Wh × 单价 + 500) / 1000}，总额 = 电费金额 + 服务费金额（快照恒等，避免合并取整的凑整歧义）。</p>
 *
 * @date 2026-09-05
 */
public final class ChargeFeeCalculator {

    private ChargeFeeCalculator() {
    }

    /**
     * 计算两段充电费用
     *
     * @param energyWh         充电电量（瓦时，≥0）
     * @param elecPriceFen     电费单价（分/千瓦时，≥0）
     * @param servicePriceFen  服务费单价（分/千瓦时，≥0）
     * @return 两段金额与总额（均为分）
     * @throws IllegalArgumentException 负数入参（数据损坏/配置错误保护，不静默）
     */
    public static ChargeFeeResult calculate(long energyWh, int elecPriceFen, int servicePriceFen) {
        if (energyWh < 0) {
            throw new IllegalArgumentException("电量不能为负：" + energyWh);
        }
        if (elecPriceFen < 0 || servicePriceFen < 0) {
            throw new IllegalArgumentException("费率不能为负：电费=" + elecPriceFen + "，服务费=" + servicePriceFen);
        }
        long elecAmountFen = (energyWh * elecPriceFen + 500) / 1000;
        long serviceAmountFen = (energyWh * servicePriceFen + 500) / 1000;
        return new ChargeFeeResult(elecAmountFen, serviceAmountFen, elecAmountFen + serviceAmountFen);
    }

    /**
     * 两段费用结果（金额单位：分）
     *
     * @param elecAmountFen    电费金额
     * @param serviceAmountFen 服务费金额
     * @param amountFen        总额（恒等于前两者之和）
     */
    public record ChargeFeeResult(long elecAmountFen, long serviceAmountFen, long amountFen) {
    }
}
