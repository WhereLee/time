package com.reason.sim.reporter;

import com.reason.sim.model.BarrierState;

/**
 * 状态上报通道（杆 -> 平台）
 *
 * <p>接口化让杆对象不依赖 HTTP：动作完成后调用 report 通知平台；
 * 真实语义=设备主动上报，网络失败由实现方处理（打日志告警，重试为后续块）。</p>
 */
public interface EventReporter {

    /**
     * 上报杆的状态变化
     *
     * @param deviceNo 设备编号
     * @param state    新状态
     */
    void report(String deviceNo, BarrierState state);
}
