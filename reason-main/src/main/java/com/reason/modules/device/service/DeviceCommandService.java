package com.reason.modules.device.service;

import com.reason.modules.device.entity.DeviceCommandLogEntity;

/**
 * 设备指令服务（管理端/自动任务远程动作的统一入口——"控制器"职责）
 *
 * <p>职责：校验档案 → 生成 seq（Redis INCR，幂等键）→ 记流水（PENDING）→ HTTP 下发 →
 * 失败记账。铁律不破：指令发出后不修改台账状态——状态只能由设备上报的事件驱动更新
 * （平台是真相的读者，不是真相的书写者）。</p>
 *
 * <p>三种触发源的失败语义不同：
 * 手动（MANUAL）失败当场抛错——人在等结果；
 * 自动规则（AUTO_RULE）失败只记账不抛——任务不因单台设备中断，下一轮对账自然重试；
 * 超时重试（TIMEOUT_RETRY）重发同 seq——设备侧幂等去重，重试计数防无限重试。</p>
 */
public interface DeviceCommandService {

    /**
     * 管理端手动升杆（失败抛 RRException 给操作者；@ManualHold 切面同时开启保持期）
     */
    void open(String deviceNo);

    /**
     * 管理端手动降杆（同上）
     */
    void close(String deviceNo);

    /**
     * 自动规则校正下发（barrierAutoTask 调用；失败不抛，记账+日志，下一轮对账吸收）
     *
     * @param action OPEN/CLOSE
     */
    void sendByRule(String deviceNo, String action);

    /**
     * 超时重试（barrierMonitorTask 调用）：重发原指令的同 seq——
     * 设备若已执行过（事件丢失导致平台没收到），凭 seq 幂等忽略；
     * 设备若真没收到过，这次正常执行。无论下发成败重试计数 +1（分层上限防无限重试）。
     */
    void retryPending(DeviceCommandLogEntity pendingLog);
}
