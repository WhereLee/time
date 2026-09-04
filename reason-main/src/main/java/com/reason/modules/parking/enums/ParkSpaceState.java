package com.reason.modules.parking.enums;

/**
 * 车位状态（与 park_space.space_state 列一一对应）
 *
 * <p>车位状态迁移不走应用层守卫——占用/释放由条件更新 SQL 原子完成
 * （UPDATE ... WHERE space_state=0 / =1，行数 0 即冲突），
 * 见 ParkSessionServiceImpl 入场/取消事务。本枚举仅提供语义码映射。</p>
 */
public enum ParkSpaceState {

    /** 空闲（0）：可入场 */
    IDLE(0, "空闲"),
    /** 占用（1）：存在进行中会话 */
    OCCUPIED(1, "占用"),
    /** 禁用（2）：不可入场（删除的替代语义，可解禁） */
    DISABLED(2, "禁用");

    private final int code;
    private final String desc;

    ParkSpaceState(int code, String desc) {
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
    public static ParkSpaceState of(int code) {
        for (ParkSpaceState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的车位状态码：" + code);
    }
}
