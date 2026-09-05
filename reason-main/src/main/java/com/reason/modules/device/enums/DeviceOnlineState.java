package com.reason.modules.device.enums;

/**
 * 设备在线状态（与 device_online.device_state 列一一对应）
 *
 * <p>在线态由心跳驱动：收到心跳置在线，心跳超时（30s）判离线。
 * 与业务态（车位占用/桩充电中）分离——设备"在线"只表示可通信，不代表业务可用。</p>
 */
public enum DeviceOnlineState {
    /** 离线（心跳超时或从未上线） */
    OFFLINE(0, "离线"),
    /** 在线（最近心跳窗口内） */
    ONLINE(1, "在线");

    private final int code;
    private final String desc;

    DeviceOnlineState(int code, String desc) {
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
    public static DeviceOnlineState of(int code) {
        for (DeviceOnlineState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的设备在线状态码：" + code);
    }
}
