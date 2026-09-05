package com.reason.modules.device.enums;

/**
 * 设备类型（与 device_online.device_type 列一一对应）
 *
 * <p>设备域统一台账语义：闸机（出入口）、位检（车位）、充电桩（绑车位）。
 * 业务台账仍分别在 parking/charging 各自域，本枚举只标识"在线台账"中的设备品类。</p>
 */
public enum DeviceType {
    /** 入口闸机（出入口放行） */
    ENTRY_GATE(0, "入口闸机"),
    /** 出口闸机（出场收费放行） */
    EXIT_GATE(1, "出口闸机"),
    /** 位检（车位占用检测，车位级追踪的停定事件源） */
    SENSOR(2, "位检"),
    /** 充电桩（与 charging_pile 桩编号一致，在线态与业务态分离） */
    CHARGING_PILE(3, "充电桩");

    private final int code;
    private final String desc;

    DeviceType(int code, String desc) {
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
     * 按类型码取枚举
     *
     * @param code DB 类型码
     * @return 枚举；未知码抛 IllegalArgumentException（数据损坏保护，不静默）
     */
    public static DeviceType of(int code) {
        for (DeviceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的设备类型码：" + code);
    }
}
