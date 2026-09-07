package com.reason.modules.device.service;

import com.reason.modules.device.entity.DeviceCommandLogEntity;
import lombok.Data;

/**
 * 设备指令服务（管理端/自动任务/监控任务与设备交互的统一入口——"控制器"职责）
 *
 * <p>职责：校验档案 → 生成 seq（Redis INCR，幂等键）→ 记流水（PENDING）→ HTTP 下发 →
 * 失败记账。铁律不破：指令发出后不修改台账状态——状态只能由设备上报的事件驱动更新
 * （平台是真相的读者，不是真相的书写者）。</p>
 *
 * <p>三种触发源的失败语义不同：
 * 手动（MANUAL）失败当场抛错——人在等结果；
 * 自动规则（AUTO_RULE）失败只记账不抛——任务不因单台设备中断，下一轮对账自然重试；
 * 超时重试（TIMEOUT_RETRY）重发同 seq——设备侧幂等去重，重试计数防无限重试。
 * 状态查询（QUERY_STATE，T20）不属于动作：不生成 seq、不进流水——它是监控对账与
 * 上行故障诊断时拿设备实况的通道（替代查自己写的台账快照）。</p>
 */
public interface DeviceCommandService {

    /**
     * 状态查询结果（QUERY_STATE 应答——设备实况，非平台台账快照；见 contracts/PROTOCOL-V2.md §2.2）
     */
    @Data
    class QueryResult {
        /** 设备编号 */
        private String deviceNo;
        /** 设备当前状态码（DeviceState/BarrierState 对齐） */
        private Integer state;
        /** 设备当前代际（重启后变化） */
        private String bootId;
        /** 设备已发出的事件序号（诊断用） */
        private Long eventSeq;
        /** 设备最近受理的指令 seq（诊断用；判断平台在途指令是否已被更新指令覆盖） */
        private Long lastCommandSeq;
    }

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

    /**
     * 状态查询（QUERY_STATE）：主动问设备"你现在是什么状态"——
     * 用于监控超时对账（T20：替代查自己写的台账快照，上行断时快照陈旧会误判）与人工诊断。
     *
     * @return 设备实况；null=查询失败（设备无响应/被拒/协议异常——与"查到非目标态"区分）
     */
    QueryResult queryState(String deviceNo);
}
