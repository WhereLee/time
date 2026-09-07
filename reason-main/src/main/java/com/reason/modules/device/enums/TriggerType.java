package com.reason.modules.device.enums;

/**
 * 指令触发源（device_command_log.trigger_type）
 *
 * <p>留痕"谁让它动的"：管理端手动（人）、自动规则（时间）、超时重试（系统补偿）。
 * 触发源是"手动 vs 自动打架"边界的审计依据——手动指令会开启保持期（@ManualHold），
 * 期间自动规则让位。</p>
 */
public enum TriggerType {

    /** 管理端手动：人在界面上点的升/降 */
    MANUAL(1, "管理端手动"),
    /** 自动规则：Quartz 周期对账发现应然≠实然，自动校正 */
    AUTO_RULE(2, "自动规则"),
    /** 超时重试：监控任务对"待到位超时"的指令重发（同 seq 幂等） */
    TIMEOUT_RETRY(3, "超时重试");

    private final int code;
    private final String desc;

    TriggerType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
