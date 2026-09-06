package com.reason.modules.device.enums;

/**
 * 设备状态（台账状态快照 & 设备侧状态共用同一套语义码）
 *
 * <p>0-未接入：登记建档但设备从未上报（块1 建档默认值）；
 * 1-升起 / 2-降下 / 3-动作中：升降杆物理三态（动作中=升降进行中，非瞬时）；
 * 4-故障：动作失败/卡杆等显式异常态，交人工处置。
 * 台账上的状态是"最近一次设备告知的快照"，只能由设备事件驱动更新，禁止指令侧自改。</p>
 */
public enum DeviceState {

    /** 未接入：建档但设备从未上报 */
    NOT_CONNECTED(0, "未接入"),
    /** 升起（升到位） */
    UP(1, "升起"),
    /** 降下（降到位） */
    DOWN(2, "降下"),
    /** 动作中：升降进行中（非瞬时动作的中间态） */
    MOVING(3, "动作中"),
    /** 故障：动作失败/卡杆，显式异常态 */
    FAULT(4, "故障");

    private final int code;
    private final String desc;

    DeviceState(int code, String desc) {
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
     * 码值转枚举；未知码抛异常（快速失败，不静默返回 null 掩盖数据异常）
     */
    public static DeviceState fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("设备状态码不能为空");
        }
        for (DeviceState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知设备状态码: " + code);
    }
}
