package com.reason.modules.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.form.DeviceRecordForm;

/**
 * 设备台账服务（管理端对"设备档案"的操作：登记/分页查询）
 */
public interface DeviceRecordService extends IService<DeviceRecordEntity> {

    /**
     * 分页查询台账（deviceNo/deviceName 模糊，deviceType/deviceState 精确）
     */
    PageUtils queryPage(DeviceRecordForm form);

    /**
     * 登记一台设备（建档）：编号唯一校验；状态恒为 0-未接入，由设备事件驱动后续更新
     *
     * @param userId 登记人（当前登录用户）
     */
    void saveRecord(DeviceRecordForm form, Long userId);

    /**
     * 设备事件驱动的状态更新（反馈闭环铁律的唯一写入路径，心跳校正入口——无事件序守卫）：
     * 台账状态只信设备上报，禁止指令侧/管理端直接改
     *
     * @param deviceNo 设备编号
     * @param stateCode 新状态码（DeviceState）
     */
    void updateStateByEvent(String deviceNo, int stateCode);

    /**
     * 事件通道的状态更新（协议 v2 序守卫入口）：同 bootId（设备代际）内只接受单调递增事件，
     * 重放/乱序/陈旧覆盖在此被拒；bootId 变化=设备重启新代际，接受并重置基线。
     * 与心跳入口（updateStateByEvent）分离：心跳是自述对账信号，不参与事件序守卫。
     *
     * @return true=事件被接受并落库；false=同代际旧序/重复事件，协议容忍的幂等丢弃（不告警）
     * @throws RRException 档案不存在（未登记设备上报=配置错位，显式失败）
     */
    boolean updateStateByEventWithSeq(String deviceNo, int stateCode, String bootId, long eventSeq);
}
