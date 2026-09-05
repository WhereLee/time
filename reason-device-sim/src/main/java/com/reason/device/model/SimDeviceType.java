package com.reason.device.model;

/**
 * 模拟设备类型（yml reason.device-sim.* 配置用枚举名）
 *
 * <p>与 main 侧 device_online.device_type 语义对齐：闸机（出入口）/位检（车位）/充电桩（绑车位）；
 * PARK_DEVICE 为 M1 遗留"停车位演示设备"（entry/exit 直报车位），B 块事件通道升级后退役。</p>
 */
public enum SimDeviceType {
    /** 入口闸机（无车位绑定） */
    ENTRY_GATE,
    /** 出口闸机（无车位绑定） */
    EXIT_GATE,
    /** 位检（绑定车位，停定事件源，A 块仅注册与心跳） */
    SENSOR,
    /** 充电桩（绑定车位，事件通道 start/finish/cancel） */
    CHARGING_PILE,
    /** 停车位演示设备（M1 语义：entry/exit 直报绑定车位；自动剧本唯一驱动对象） */
    PARK_DEVICE
}
