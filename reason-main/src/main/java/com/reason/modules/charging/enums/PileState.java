package com.reason.modules.charging.enums;

import com.reason.common.exception.RRException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 充电桩状态（与 charging_pile.pile_state 列一一对应）
 *
 * <p>迁移矩阵（表驱动）：空闲 ↔ 充电中，停用为终态（维护态由管理端恢复）。
 * 守卫 {@link #assertCanTransit} 为快速失败层；并发下的最终裁决由条件更新承担。</p>
 */
public enum PileState {
    /** 空闲 */
    IDLE(0, "空闲"),
    /** 充电中 */
    CHARGING(1, "充电中"),
    /** 停用 */
    DISABLED(2, "停用");

    private final int code;
    private final String desc;

    /** 合法迁移表：from → 允许的 to 集合（不可变） */
    private static final Map<PileState, Set<PileState>> TRANSITIONS;

    static {
        Map<PileState, Set<PileState>> map = new EnumMap<>(PileState.class);
        map.put(PileState.IDLE, EnumSet.of(PileState.CHARGING));
        map.put(PileState.CHARGING, EnumSet.of(PileState.IDLE));
        map.put(PileState.DISABLED, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    PileState(int code, String desc) {
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
    public static PileState of(int code) {
        for (PileState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的充电桩状态码：" + code);
    }

    /**
     * 状态迁移守卫：非法迁移抛业务异常（快速失败层，调用点在事务内）
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    public static void assertCanTransit(PileState from, PileState to) {
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new RRException("非法的充电桩状态迁移：" + from.desc + " → " + to.desc);
        }
    }
}
