package com.reason.modules.device.service;

import java.util.List;
import java.util.Map;

/**
 * 设备在线台账服务（心跳批量更新 / 未知设备告警）
 *
 * @date 2026-09-06
 */
public interface DeviceOnlineService {

    /**
     * 处理一批心跳上报（网关聚合语义，单次批量）
     *
     * <p>online=true 的设备批量置在线并刷新心跳时刻；online=false（故障注入/网关检测不可达）
     * 立即置离线；台账中不存在的设备号告警留痕（配置漂移/未登记设备接入的运维信号）。</p>
     *
     * @param reportedAt 心跳批次时刻（秒）
     * @param devices    设备在线快照列表
     */
    void recordHeartbeat(long reportedAt, List<Map<String, Object>> devices);

    /**
     * 巡检：心跳超时的在线设备批量置离线
     *
     * @param thresholdSeconds 心跳超时阈值（秒）
     * @return 本次置离线台数
     */
    int offlineByTimeout(long thresholdSeconds);
}
