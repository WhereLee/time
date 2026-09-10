package com.reason.modules.device.service;

/**
 * 设备监控服务（心跳/在线/对账——"十公里外不可靠"的平台侧应对）
 *
 * <p>在线判定用 Redis TTL 而非库表：心跳写 key（TTL=超时阈值），key 存在即在线、
 * 过期即离线——"把过期时间当业务逻辑"，无需定时扫库比对时间戳。
 * 心跳携带状态自述，与台账不一致时以设备为准校正（心跳也是设备说的话，
 * 走 updateStateByEvent 唯一合法写入路径，铁律不破）。</p>
 */
public interface DeviceMonitorService {

    /**
     * 处理设备心跳：状态对账 → 故障自述告警 → 刷新在线 key
     *
     * <p>对账分支语义（仅稳定态 UP/DOWN/FAULT 参与，MOVING 中间态忽略）：
     * 台账在 grace 窗口内刚被事件通道更新过 → 本轮让路不校正（两通道时序差，非漂移）；
     * 首次接入（台账=未接入）→ 只校正不告警（正常接入流程）；
     * 其余不一致 → 校正 + 告警 STATE_MISMATCH（真漂移：外力改态/重启/事件丢失）。</p>
     *
     * @param deviceNo  设备编号
     * @param stateCode 设备当前状态自述
     */
    void heartbeat(String deviceNo, Integer stateCode);

    /**
     * 在线判定（Redis EXISTS：在线 key 未过期）
     */
    boolean isOnline(String deviceNo);

    /**
     * 离线扫描（barrierMonitorTask 周期调用）：
     * 已接入过（状态≠未接入）但在线 key 已过期的设备 → 离线告警（去重窗口防刷屏；
     * 批次4 D-F：单轮离线数超批量阈值时合并为一条 BATCH_OFFLINE，回落自动关闭）
     */
    void scanOffline();

    /**
     * 统计在线设备数（批次4 指标：pipeline 批量 EXISTS，与离线扫描同手法——避免逐台往返）
     */
    int countOnline();
}
