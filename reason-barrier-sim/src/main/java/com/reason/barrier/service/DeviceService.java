package com.reason.barrier.service;

/**
 * 设备服务（模拟器业务编排入口——controller 只做协议翻译，业务统一收口在此）
 *
 * <p>设备侧虽无事务无数据库，但保留 service 层与平台侧分层结构对齐（示例一致性）：
 * controller = HTTP 契约翻译；service = 设备操作编排（查设备、裁决异常翻译）；Barrier = 领域执行。</p>
 */
public interface DeviceService {

    /**
     * 升杆：找到设备 -> 裁决并启动动作（异步）
     *
     * @return 回执消息（成功="指令已受理"；拒绝=具体原因）
     * @throws IllegalArgumentException 设备不存在
     */
    String open(String deviceNo);

    /**
     * 降杆：找到设备 -> 裁决并启动动作（异步）
     *
     * @return 回执消息（成功="指令已受理"；拒绝=具体原因）
     * @throws IllegalArgumentException 设备不存在
     */
    String close(String deviceNo);
}
