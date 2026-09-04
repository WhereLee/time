package com.reason.modules.parking.service;

/**
 * 计费计算器（纯函数、无状态、无副作用）
 *
 * <p>取整语义（M0 规则，fee_rule 注释固化）：</p>
 * <ul>
 *   <li>时长（分钟）：不足 1 分钟按 1 分钟向上取整——展示/优惠计算精度</li>
 *   <li>金额（分）：按小时向上取整 × 单价——纯整数算术 ((s+3599)/3600)*price，
 *       不引入浮点；0 秒输入天然返回 0，不崩</li>
 * </ul>
 *
 * <p>与规则引擎的关系：多策略/峰谷阶梯/免费时段属 M2 规则引擎演进，
 * 本类只承载"给定时长与单价，如何算钱"的单一语义。</p>
 */
public final class FeeCalculator {

    private FeeCalculator() {
    }

    /**
     * 停车时长（分钟，向上取整）
     *
     * @param durationSeconds 时长（秒），调用方保证非负
     * @return 分钟数，不足 1 分钟按 1 分钟
     */
    public static int calcDurationMinutes(long durationSeconds) {
        return (int) ((durationSeconds + 59) / 60);
    }

    /**
     * 应收金额（分，按小时向上取整）
     *
     * @param durationSeconds     时长（秒），调用方保证非负
     * @param unitPriceFenPerHour 单价（分/小时），非负
     * @return 金额（分）；0 秒返回 0
     */
    public static long calcAmountFen(long durationSeconds, int unitPriceFenPerHour) {
        long hours = (durationSeconds + 3599) / 3600;
        return hours * unitPriceFenPerHour;
    }
}
