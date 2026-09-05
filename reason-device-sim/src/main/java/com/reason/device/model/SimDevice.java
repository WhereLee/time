package com.reason.device.model;

import com.reason.device.config.DeviceSimProperties;
import lombok.Getter;

/**
 * 运行态模拟设备：静态配置 + 内存会话态
 *
 * <p>无 DB：进程重启即恢复初始态（真实设备上电无状态，会话句柄在上报响应中取得）。</p>
 */
@Getter
public class SimDevice {

    private final String deviceNo;
    private final String spaceNo;
    private final SimDeviceType deviceType;

    /** 会话态（volatile：自动剧本线程与手控线程并发读写） */
    private volatile Long sessionId;
    private volatile String plateNo;
    private volatile long sessionStartTs;

    /** 在线态（volatile：故障注入接口与心跳上报并发读写；默认在线，置离线模拟设备故障） */
    private volatile boolean online = true;

    public SimDevice(DeviceSimProperties.DeviceCfg cfg) {
        this.deviceNo = cfg.getDeviceNo();
        this.spaceNo = cfg.getSpaceNo();
        this.deviceType = cfg.getDeviceType();
    }

    public SimDevice(String deviceNo, String spaceNo, SimDeviceType deviceType) {
        this.deviceNo = deviceNo;
        this.spaceNo = spaceNo;
        this.deviceType = deviceType;
    }

    /** 是否空闲（无进行中会话） */
    public boolean isIdle() {
        return sessionId == null;
    }

    /** 是否充电桩设备（事件通道：start/finish/cancel 走 /device/charging/*） */
    public boolean isCharger() {
        return deviceType == SimDeviceType.CHARGING_PILE;
    }

    /** 是否停车位演示设备（自动剧本唯一驱动对象，M1 语义 entry/exit 直报车位） */
    public boolean isParkDevice() {
        return deviceType == SimDeviceType.PARK_DEVICE;
    }

    /** 是否在线（可被故障注入置离线；离线设备心跳上报 online=false） */
    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    /** 绑定入场上报结果（持有会话句柄） */
    public void bindSession(Long sessionId, String plateNo, long nowSeconds) {
        this.sessionId = sessionId;
        this.plateNo = plateNo;
        this.sessionStartTs = nowSeconds;
    }

    /** 出场/取消上报后清除会话态 */
    public void clearSession() {
        this.sessionId = null;
        this.plateNo = null;
        this.sessionStartTs = 0;
    }
}
