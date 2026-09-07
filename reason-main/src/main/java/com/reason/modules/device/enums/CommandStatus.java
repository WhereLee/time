package com.reason.modules.device.enums;

/**
 * 指令流水状态（device_command_log.command_status）
 *
 * <p>指令的账本状态机：下发（PENDING）→ 设备事件回报到位（ARRIVED）；
 * 下发即失败（SEND_FAILED，网络/设备当场拒绝）；执行中故障（EXEC_FAILED，设备上报 FAULT）；
 * 超时重试超限（RETRY_EXCEEDED，转告警人工）。
 * 账本只记"平台视角的指令生命周期"，不代表设备真实状态（那是台账的事）。</p>
 */
public enum CommandStatus {

    /** 已下发待到位：指令被设备受理，等待状态事件回报 */
    PENDING(0, "已下发待到位"),
    /** 已到位：设备事件回报的目标状态与指令一致，闭环完成 */
    ARRIVED(1, "已到位"),
    /** 下发失败：网络不可达或设备当场拒绝（非法状态/防砸互锁） */
    SEND_FAILED(2, "下发失败"),
    /** 重试超限转故障：多次重发同 seq 仍未到位，停止重试并告警 */
    RETRY_EXCEEDED(3, "重试超限转故障"),
    /** 执行失败：设备执行中上报 FAULT（卡杆等），指令中断不再重试，等人工复位 */
    EXEC_FAILED(4, "执行失败(设备故障)"),
    /** 已被更新指令取代（0.2 代际裁决：更晚代际指令已下发，本指令永不会执行，终止挂账） */
    SUPERSEDED(5, "被更新指令取代");

    private final int code;
    private final String desc;

    CommandStatus(int code, String desc) {
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
