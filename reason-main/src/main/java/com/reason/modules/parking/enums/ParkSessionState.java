package com.reason.modules.parking.enums;

import com.reason.common.exception.RRException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 停车会话状态（与 park_session.session_state 列一一对应）
 *
 * <p>迁移矩阵（表驱动）：仅「进行中」可迁移，已结束/已取消为终态不可逆。
 * 守卫 {@link #assertCanTransit} 为快速失败层；并发下的最终裁决由条件更新
 * （UPDATE ... WHERE session_state=0）承担——见 ParkSessionServiceImpl。</p>
 */
public enum ParkSessionState {

    /** 进行中（0）：可出场、可取消 */
    ONGOING(0, "进行中"),
    /** 已结束（1）：出场结算完成，终态 */
    FINISHED(1, "已结束"),
    /** 已取消（2）：终态，车位已释放 */
    CANCELLED(2, "已取消");

    private final int code;
    private final String desc;

    /** 合法迁移表：from → 允许的 to 集合（不可变） */
    private static final Map<ParkSessionState, Set<ParkSessionState>> TRANSITIONS;

    static {
        Map<ParkSessionState, Set<ParkSessionState>> map = new EnumMap<>(ParkSessionState.class);
        map.put(ONGOING, EnumSet.of(FINISHED, CANCELLED));
        map.put(FINISHED, Collections.emptySet());
        map.put(CANCELLED, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    ParkSessionState(int code, String desc) {
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
     * @param code DB 状态码（0/1/2）
     * @return 枚举；未知码抛 IllegalArgumentException（数据损坏保护，不静默）
     */
    public static ParkSessionState of(int code) {
        for (ParkSessionState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的停车会话状态码：" + code);
    }

    /**
     * 状态迁移守卫：非法迁移抛业务异常（快速失败层，调用点在事务内）
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    public static void assertCanTransit(ParkSessionState from, ParkSessionState to) {
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new RRException("非法的会话状态迁移：" + from.desc + " → " + to.desc);
        }
    }
}
