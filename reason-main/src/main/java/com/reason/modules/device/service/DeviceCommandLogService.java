package com.reason.modules.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.enums.TriggerType;
import com.reason.modules.device.form.DeviceCommandLogForm;

import java.util.List;

/**
 * 设备指令流水服务（指令闭环的账本）
 *
 * <p>账本回答三个问题：平台发过什么指令（seq 幂等键）、等到到位没有（PENDING→ARRIVED）、
 * 没等到怎么办（超时重试→超限告警）。它是"反馈闭环"里平台侧的记忆——
 * 指令发出后平台不再盲信"设备会做到"，而是记账、等事件、对账、补偿。</p>
 */
public interface DeviceCommandLogService extends IService<DeviceCommandLogEntity> {

    /**
     * 生成设备内单调递增的指令序号（Redis INCR：原子、跨重启持久、多实例平台共享；
     * DB MAX(seq)+1 有并发竞态，不可用）
     */
    long nextSeq(String deviceNo);

    /**
     * 记录一条"已下发待到位"流水
     *
     * @param trigger 触发源（手动/自动规则/超时重试——审计"谁让它动的"）
     * @return 落库后的流水（含 commandId）
     */
    DeviceCommandLogEntity recordPending(String deviceNo, String action, long seq, TriggerType trigger);

    /**
     * 下发失败（网络不可达/设备当场拒绝）：流水置 SEND_FAILED，不进重试队列
     */
    void markSendFailed(Long commandId);

    /**
     * 设备事件回报到位：找该设备最近一条"待到位且动作匹配"的流水置 ARRIVED
     *
     * @return 是否命中流水（false=事件与任何在途指令无关，如外力改态/心跳自述）
     */
    boolean markArrived(String deviceNo, String action);

    /**
     * 设备上报 FAULT：该设备所有在途指令置 EXEC_FAILED（执行中断，不再重试，等人工复位）
     */
    void markExecFailed(String deviceNo);

    /**
     * 超时扫描：所有"待到位且下发时间早于 now-timeoutSeconds"的流水
     */
    List<DeviceCommandLogEntity> findTimeoutPending(int timeoutSeconds);

    /**
     * 重试计数 +1（SQL 原子自增，monitor 任务热路径）
     */
    void markRetried(Long commandId);

    /**
     * 重试超限：流水置 RETRY_EXCEEDED（CAS：仅当仍为 PENDING 才生效）
     *
     * @return 是否命中（false = 已被并发路径推进，如设备事件恰好到位——调用方据此放弃告警）
     */
    boolean markRetryExceeded(Long commandId);

    /**
     * 管理端分页查询
     */
    PageUtils queryPage(DeviceCommandLogForm form);
}
