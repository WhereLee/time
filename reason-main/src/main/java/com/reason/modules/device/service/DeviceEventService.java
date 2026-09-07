package com.reason.modules.device.service;

import com.reason.modules.device.form.DeviceEventForm;

/**
 * 设备事件处理编排（事件到达后平台要做的全部事，收口在此）
 *
 * <p>controller 只做协议翻译，"一个状态事件意味着什么"由本服务回答：
 * 更新台账（铁律唯一写入路径，协议 v2 带序守卫）→ 按证据推进指令流水 → 故障告警。
 * 编排独立成对象的价值：事件语义变更（如未来加事件类型）只改这一处。</p>
 */
public interface DeviceEventService {

    /**
     * 处理设备状态事件（协议 v2：见 contracts/PROTOCOL-V2.md §3.2）
     *
     * <p>事件-平台动作映射（销账必须证据驱动）：
     * UP/DOWN + commandSeq 非空 = 指令到位 → 台账序更新 + 按 (deviceNo, seq) 精确销账对应动作流水；
     * UP/DOWN + commandSeq 空 = 外力改态 → 只更新台账，不动流水（不再"按动作猜"）；
     * MOVING = 中间态 → 只更新台账（流水继续等到位）；
     * FAULT = 故障 → 台账更新 + 在途流水全部置 EXEC_FAILED（执行中断）+ 告警交人工（0.6 细化为按 seq 归属）。
     * 同代际旧序事件（重放/乱序）被序守卫拒绝后幂等丢弃，不推进任何流水。</p>
     *
     * @param form 事件表单（协议 v2：含 commandSeq/bootId/eventSeq）
     */
    void handleStateEvent(DeviceEventForm form);
}
