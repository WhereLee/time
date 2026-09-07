package com.reason.modules.device.enums;

/**
 * 告警类型（device_alarm.alarm_type）
 *
 * <p>告警 = "故障态+告警"机制的落点：平台发现异常只能喊，不能替设备写状态
 * （台账状态仍只信设备事件——铁律不破）。每类告警对应一个真实边界：
 * RETRY_EXCEEDED=重试幂等分层上限；OFFLINE=断电/断网；
 * STATE_MISMATCH=外力改态/时钟漂移被对账发现；DEVICE_FAULT=卡杆。</p>
 */
public enum AlarmType {

    /** 指令重试超限：下发后多次重试仍未到位（机械卡死/设备失联），转人工 */
    RETRY_EXCEEDED(1, "指令重试超限"),
    /** 设备离线：心跳 key 过期（TTL 判离线），断电/断网/进程挂 */
    OFFLINE(2, "设备离线"),
    /** 状态对账不一致：心跳自述与台账快照不符（外力改态/漂移），已由心跳校正 */
    STATE_MISMATCH(3, "状态对账不一致"),
    /** 设备故障上报：设备侧显式上报 FAULT（卡杆等机械故障） */
    DEVICE_FAULT(4, "设备故障上报");

    private final int code;
    private final String desc;

    AlarmType(int code, String desc) {
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
