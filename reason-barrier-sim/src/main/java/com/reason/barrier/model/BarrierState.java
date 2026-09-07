package com.reason.barrier.model;

/**
 * 杆的物理状态（码值与平台侧 DeviceState 对齐：1-升起 2-降下 3-动作中 4-故障）
 *
 * <p>真实语义：杆的升降不是瞬时的——"动作中"是执行指令后的物理中间态，
 * 到位后停驻。状态由设备自持，平台只是被告知者。</p>
 */
public enum BarrierState {
    /** 升起（升到位） */
    UP(1),
    /** 降下（降到位，默认初始态） */
    DOWN(2),
    /** 动作中：正在升/降（非瞬时动作的中间态） */
    MOVING(3),
    /** 故障：动作失败/卡杆（样例保留码位，故障注入为后续块） */
    FAULT(4);

    private final int code;

    BarrierState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
