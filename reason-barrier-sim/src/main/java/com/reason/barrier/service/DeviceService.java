package com.reason.barrier.service;

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
     * @param seq 平台指令序号（幂等键：重复/乱序指令被设备吸收）
     * @return 回执消息（"指令已受理" 或 "重复指令已幂等忽略"）
     * @throws IllegalStateException    动作在当前状态不合法（设备拒绝）
     * @throws IllegalArgumentException 设备不存在
     */
    String open(String deviceNo, long seq);

    /**
     * 降杆：同上（防砸互锁在此生效：杆下有车拒绝降杆）
     */
    String close(String deviceNo, long seq);

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
}
