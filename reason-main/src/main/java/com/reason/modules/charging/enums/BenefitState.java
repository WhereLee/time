package com.reason.modules.charging.enums;

import com.reason.common.exception.RRException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 免停权益状态（与 benefit_record.benefit_state 列一一对应）
 *
 * <p>迁移矩阵（表驱动）：仅「可用」可迁移：核销（出场判官）/过期（调度 job）双路径，终态不可逆。
 * 守卫 {@link #assertCanTransit} 为快速失败层；并发下的最终裁决由条件更新承担。</p>
 */
public enum BenefitState {
    /** 可用 */
    AVAILABLE(0, "可用"),
    /** 已核销 */
    REDEEMED(1, "已核销"),
    /** 已过期 */
    EXPIRED(2, "已过期");

    private final int code;
    private final String desc;

    /** 合法迁移表：from → 允许的 to 集合（不可变） */
    private static final Map<BenefitState, Set<BenefitState>> TRANSITIONS;

    static {
        Map<BenefitState, Set<BenefitState>> map = new EnumMap<>(BenefitState.class);
        map.put(BenefitState.AVAILABLE, EnumSet.of(BenefitState.REDEEMED, BenefitState.EXPIRED));
        map.put(BenefitState.REDEEMED, Collections.emptySet());
        map.put(BenefitState.EXPIRED, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    BenefitState(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按状态码取枚举
     *
     * @param code DB 状态码
     * @return 枚举；未知码抛 IllegalArgumentException（数据损坏保护，不静默）
     */
    public static BenefitState of(int code) {
        for (BenefitState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的权益状态码：" + code);
    }

    /**
     * 状态迁移守卫：非法迁移抛业务异常（快速失败层，调用点在事务内）
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    public static void assertCanTransit(BenefitState from, BenefitState to) {
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new RRException("非法的权益状态迁移：" + from.desc + " → " + to.desc);
        }
    }
}
