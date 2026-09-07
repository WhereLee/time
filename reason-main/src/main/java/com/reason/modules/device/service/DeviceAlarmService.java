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
     * 触发告警（带 Redis SETNX 去重窗口：同设备同类型窗口内只落一条；
     * Redis 丢失后由 DB 时间窗查重兜底防刷屏——0.8）
     *
     * @param deviceNo 设备编号
     * @param type     告警类型
     * @param content  人可读内容
     */
    void raise(String deviceNo, AlarmType type, String content);

    /**
     * 管理端分页查询（0.4：alarmHandled 缺省时默认只出未确认告警——应急通道只看"此刻真故障"，
     * 历史已处理需显式传 alarmHandled=1）
     */
    PageUtils queryPage(DeviceAlarmForm form);

    /**
     * 告警确认处理（0.4 告警闭环：alarm_handled 0->1 CAS，记录处理人/时间）——
     * 告警只喊不行，必须能"处置+关闭"，否则未处理列表永远等于全表，应急通道疲劳
     *
     * @param alarmId 告警 ID
     * @param userId  处理人（当前登录用户）
     */
    void handle(Long alarmId, Long userId);

    /**
     * 恢复事件反向标记（0.4）：设备状态恢复（到位事件/心跳稳定）后，该设备未处理的
     * 状态类告警（离线/动作卡死/自动校正失败）自动置已处理——"故障消失"与"告警关闭"对齐
     *
     * @return 自动关闭的告警条数
     */
    int markRecovered(String deviceNo);

    /**
     * 心跳恢复反向标记（阶段1 实测暴露补漏）：设备离线判定的唯一权威 = 心跳 TTL 过期，
     * 因此恢复的权威 = 心跳再次到达——收到一次成功心跳即自动关闭该设备未处理的 OFFLINE 告警。
     *
     * <p>只关 OFFLINE：MOVING_STUCK/AUTO_CORRECT_FAILED 需要事件级证据（动作到位/校正闭环），
     * 心跳粒度够不到（卡动作中的设备心跳正常）——那些仍由 {@link #markRecovered} 在事件路径关。</p>
     *
     * @return 自动关闭的告警条数
     */
    int markOnlineRecovered(String deviceNo);
}
