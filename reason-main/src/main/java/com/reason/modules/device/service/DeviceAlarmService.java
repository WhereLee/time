package com.reason.modules.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.device.entity.DeviceAlarmEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.form.DeviceAlarmForm;

/**
 * 设备告警服务（"故障态+告警"机制的落点）
 *
 * <p>告警只"喊"不改状态：任何异常（重试超限/离线/对账不一致/设备故障）显式落库交给人，
 * 台账状态仍只信设备事件（铁律不破）。同类告警在去重窗口内只落一条（持续异常不刷屏）。</p>
 */
public interface DeviceAlarmService extends IService<DeviceAlarmEntity> {

    /**
     * 触发告警（带 Redis SETNX 去重窗口：同设备同类型窗口内只落一条）
     *
     * @param deviceNo 设备编号
     * @param type     告警类型
     * @param content  人可读内容
     */
    void raise(String deviceNo, AlarmType type, String content);

    /**
     * 管理端分页查询
     */
    PageUtils queryPage(DeviceAlarmForm form);
}
