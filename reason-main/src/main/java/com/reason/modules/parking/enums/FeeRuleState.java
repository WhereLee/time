package com.reason.modules.parking.enums;

/**
 * 计费规则状态（与 fee_rule.rule_state 列一一对应）
 *
 * <p>规则状态为纯标记（启停），无迁移矩阵守卫需求。</p>
 */
public enum FeeRuleState {

    /** 停用（0）：结算时不可被读取 */
    DISABLED(0, "停用"),
    /** 启用（1）：M0 仅一条启用规则 */
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    FeeRuleState(int code, String desc) {
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
     * @param code DB 状态码（0/1）
     * @return 枚举；未知码抛 IllegalArgumentException（数据损坏保护，不静默）
     */
    public static FeeRuleState of(int code) {
        for (FeeRuleState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的计费规则状态码：" + code);
    }
}
