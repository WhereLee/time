package com.reason.modules.device.service;

/**
 * 设备指令服务（管理端远程动作的入口——"控制器"职责）
 *
 * <p>职责：校验档案存在 → 组装指令 → 经设备通道下发（HTTP）→ 结果记审计。
 * 关键铁律：指令发出后<b>不修改台账状态</b>——状态只能由设备上报的事件驱动更新
 * （反馈闭环：平台是真相的读者，不是真相的书写者）。</p>
 */
public interface DeviceCommandService {

    /**
     * 下发升杆指令（设备侧裁决合法性：降下态才允许升起）
     *
     * @param deviceNo 目标设备编号
     */
    void open(String deviceNo);

    /**
     * 下发降杆指令（设备侧裁决合法性：升起态才允许降下）
     *
     * @param deviceNo 目标设备编号
     */
    void close(String deviceNo);
}
