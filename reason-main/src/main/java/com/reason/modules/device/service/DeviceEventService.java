package com.reason.modules.device.service;

/**
 * 设备事件处理编排（事件到达后平台要做的全部事，收口在此）
 *
 * <p>controller 只做协议翻译，"一个状态事件意味着什么"由本服务回答：
 * 更新台账（铁律唯一写入路径）→ 推进指令流水（到位闭环）→ 故障告警。
 * 编排独立成对象的价值：事件语义变更（如未来加事件类型）只改这一处。</p>
 */
public interface DeviceEventService {

    /**
     * 处理设备状态事件
     *
     * <p>分状态语义：
     * UP/DOWN = 到位事件 → 台账更新 + 对应动作的在途流水置 ARRIVED（指令闭环完成）；
     * MOVING = 中间态 → 只更新台账（流水继续等到位）；
     * FAULT = 故障 → 台账更新 + 在途流水全部置 EXEC_FAILED（执行中断）+ 告警交人工。</p>
     *
     * @param deviceNo  设备编号
     * @param stateCode 设备上报的状态码（DeviceState）
     */
    void handleStateEvent(String deviceNo, int stateCode);
}
