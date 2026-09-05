package com.reason.modules.charging.enums;

import com.reason.common.exception.RRException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 充电会话状态（与 charge_session.session_state 列一一对应）
 *
 * <p>迁移矩阵（表驱动）：仅「充电中」可迁移，已结束/已取消/超时结束为终态不可逆。
 * 守卫 {@link #assertCanTransit} 为快速失败层；并发下的最终裁决由条件更新承担。</p>
 */
public enum ChargeSessionState {
    /** 充电中 */
    CHARGING(0, "充电中"),
    /** 已结束 */
    FINISHED(1, "已结束"),
    /** 已取消 */
    CANCELLED(2, "已取消"),
    /** 超时结束 */
    TIMEOUT_FINISHED(3, "超时结束");

    private final int code;
    private final String desc;

    /** 合法迁移表：from → 允许的 to 集合（不可变） */
    private static final Map<ChargeSessionState, Set<ChargeSessionState>> TRANSITIONS;

    static {
        Map<ChargeSessionState, Set<ChargeSessionState>> map = new EnumMap<>(ChargeSessionState.class);
        map.put(ChargeSessionState.CHARGING, EnumSet.of(ChargeSessionState.FINISHED, ChargeSessionState.CANCELLED, ChargeSessionState.TIMEOUT_FINISHED));
        map.put(ChargeSessionState.FINISHED, Collections.emptySet());
        map.put(ChargeSessionState.CANCELLED, Collections.emptySet());
        map.put(ChargeSessionState.TIMEOUT_FINISHED, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    ChargeSessionState(int code, String desc) {
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
    public static ChargeSessionState of(int code) {
        for (ChargeSessionState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的充电会话状态码：" + code);
    }

    /**
     * 状态迁移守卫：非法迁移抛业务异常（快速失败层，调用点在事务内）
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    public static void assertCanTransit(ChargeSessionState from, ChargeSessionState to) {
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new RRException("非法的充电会话状态迁移：" + from.desc + " → " + to.desc);
        }
    }
}
