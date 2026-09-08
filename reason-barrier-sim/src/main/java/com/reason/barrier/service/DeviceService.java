package com.reason.barrier.service;

import java.util.Map;

/**
 * 设备服务（模拟器业务编排入口——controller 只做协议翻译，业务统一收口在此）
 *
 * <p>设备侧虽无事务无数据库，但保留 service 层与平台侧分层结构对齐（示例一致性）：
 * controller = HTTP 契约翻译；service = 设备操作编排（查设备、裁决异常翻译）；Barrier = 领域执行。</p>
 *
 * <p>回执契约：正常返回 String = 受理成功（含幂等忽略）；
 * 抛 IllegalStateException = 设备拒绝动作（状态机/防砸互锁/故障态裁决）；
 * 抛 IllegalArgumentException = 设备不存在。controller 据此翻译 code=0/1。</p>
 */
public interface DeviceService {

    /**
     * 升杆：找到设备 -> 裁决并启动动作（异步）
     *
     * @param seq     平台指令序号（幂等键：重复/乱序指令被设备吸收）
     * @param traceId 链路跟踪号（平台下发携带，沿用到动作与事件上报）
     * @return 回执消息（"指令已受理" 或 "重复指令已幂等忽略"）
     * @throws IllegalStateException    动作在当前状态不合法（设备拒绝）
     * @throws IllegalArgumentException 设备不存在
     */
    String open(String deviceNo, long seq, String traceId);

    /**
     * 降杆：同上（防砸互锁在此生效：杆下有车拒绝降杆）
     */
    String close(String deviceNo, long seq, String traceId);

    /**
     * 故障注入（扮演物理世界的意外）：下一个动作将卡杆转 FAULT
     */
    String injectFault(String deviceNo);

    /**
     * 人工复位：清除故障；杆停在 FAULT 时复位回落 DOWN 并上报
     */
    String recover(String deviceNo);

    /**
     * 外力改态：物理世界把杆掰到 UP/DOWN（不经指令），设备主动上报
     */
    String tamper(String deviceNo, String state);

    /**
     * 防砸信号更新：杆下探测器检测到车进出
     */
    String setVehicle(String deviceNo, boolean present);

    /**
     * 静默故障注入（0.3 卡滞不终态）：受理但无动作无上报——验证平台自动校正熔断
     */
    String setStuck(String deviceNo, boolean stuck);

    /**
     * 卡动作中注入（0.4）：动作到 MOVING 后永不终态——验证平台 MOVING 巡检告警
     */
    String setStuckMoving(String deviceNo, boolean stuck);

    /**
     * 状态查询（QUERY_STATE，协议 v2 §2.2）：返回设备实况快照——
     * 平台监控对账与上行故障诊断时主动询问（替代查平台自己的台账快照）
     *
     * @return 快照 {deviceNo, state(code), bootId, eventSeq, lastCommandSeq}
     * @throws IllegalArgumentException 设备不存在
     */
    Map<String, Object> queryState(String deviceNo);
}
