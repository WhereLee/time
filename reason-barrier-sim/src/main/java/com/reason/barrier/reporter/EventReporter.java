package com.reason.barrier.reporter;

import com.reason.barrier.model.BarrierState;

/**
 * 状态上报通道（杆 -> 平台，协议 v2，见 contracts/PROTOCOL-V2.md §3）
 *
 * <p>接口化让杆对象不依赖 HTTP：状态变化后调用 report 通知平台。
 * 事件载荷（协议 v2）：commandSeq（引起变化的指令序号，非指令驱动=null）、
 * bootId（设备重启代际）、eventSeq（设备内单调事件序号）——平台凭 eventSeq 拒绝重放/乱序、
 * 凭 commandSeq 精确销账。</p>
 *
 * <p>traceId（批次1 可观测性，D-E）：显式参数传递而非 MDC 隐式捕——上报跨两级异步边界
 * （动作线程 → 发送线程），显式传递可靠且可追溯；各实现负责将其载入 HTTP header / MQ property。</p>
 */
public interface EventReporter {

    /**
     * 上报杆的状态变化（协议 v2）
     *
     * @param deviceNo   设备编号
     * @param state      新状态
     * @param commandSeq 引起本次状态变化的指令序号（非指令驱动=null，如外力改态/人工复位）
     * @param bootId     设备重启代际
     * @param eventSeq   设备内单调递增事件序号
     * @param traceId    链路跟踪号（平台下发沿用/设备自发生成）
     */
    void report(String deviceNo, BarrierState state, Long commandSeq, String bootId, long eventSeq, String traceId);
}
