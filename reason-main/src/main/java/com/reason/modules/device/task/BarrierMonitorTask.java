package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceCommandService;
import com.reason.modules.device.service.DeviceMonitorService;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 升降杆超时与离线监控任务（barrierMonitorTask，Quartz 每 30 秒）
 *
 * <p>"十公里外不可靠"的平台侧兜底，两件事：
 * <ol>
 *   <li>指令超时对账：下发后超过阈值仍"待到位"（事件丢失/设备卡死/网络断）→
 *       <b>先看台账实然</b>：若心跳对账已把台账校正到指令目标态（事件丢了但动作实际完成），
 *       直接销账 ARRIVED 不重试；否则重发同 seq（设备幂等去重，不会重复动作）；
 *       重试达上限 → 停止重试、显式告警交人工（"重试幂等分层上限"边界）；</li>
 *   <li>离线扫描：Redis 在线 key 过期（TTL 判离线）→ 告警（去重窗口防刷屏）。</li>
 * </ol>
 * 注意本任务只告警不改台账状态——平台发现异常只能"喊"，状态仍只信设备事件（铁律不破）。</p>
 */
@Slf4j
@Component("barrierMonitorTask")
public class BarrierMonitorTask implements ITask {

    private final BarrierProperties properties;
    private final DeviceCommandLogService commandLogService;
    private final DeviceCommandService commandService;
    private final DeviceAlarmService alarmService;
    private final DeviceMonitorService monitorService;
    private final DeviceRecordDao recordDao;

    public BarrierMonitorTask(BarrierProperties properties,
                              DeviceCommandLogService commandLogService,
                              DeviceCommandService commandService,
                              DeviceAlarmService alarmService,
                              DeviceMonitorService monitorService,
                              DeviceRecordDao recordDao) {
        this.properties = properties;
        this.commandLogService = commandLogService;
        this.commandService = commandService;
        this.alarmService = alarmService;
        this.monitorService = monitorService;
        this.recordDao = recordDao;
    }

    @Override
    public void run(String params) {
        //1. 指令超时对账：待到位且超过阈值的流水
        List<DeviceCommandLogEntity> timeouts =
                commandLogService.findTimeoutPending(properties.getCommandTimeoutSeconds());
        for (DeviceCommandLogEntity pending : timeouts) {
            //先对账销账：事件通道可能丢了"到位"事件，但心跳对账已把台账校正到目标态——
            //动作实际完成，直接销账 ARRIVED，不重试不误告警（双通道互补：心跳兜事件）
            int targetState = "OPEN".equals(pending.getCommandAction())
                    ? DeviceState.UP.getCode() : DeviceState.DOWN.getCode();
            DeviceRecordEntity record = recordDao.selectOne(new LambdaQueryWrapper<DeviceRecordEntity>()
                    .eq(DeviceRecordEntity::getDeviceNo, pending.getDeviceNo()));
            if (record != null && record.getDeviceState() != null
                    && record.getDeviceState() == targetState) {
                commandLogService.markArrived(pending.getDeviceNo(), pending.getCommandAction());
                log.info("超时指令经台账对账确认已到位，销账 deviceNo={} action={} seq={}（到位事件丢失，心跳对账兜底）",
                        pending.getDeviceNo(), pending.getCommandAction(), pending.getCommandSeq());
                continue;
            }

            if (pending.getRetryCount() >= properties.getMaxRetry()) {
                //分层上限触达：停止重试，账本定格 RETRY_EXCEEDED，告警交人工。
                //CAS 未命中 = 设备事件恰好在本轮扫描间隙到位（流水已 ARRIVED）——指令实际成功，不告警
                boolean marked = commandLogService.markRetryExceeded(pending.getCommandId());
                if (marked) {
                    alarmService.raise(pending.getDeviceNo(), AlarmType.RETRY_EXCEEDED,
                            String.format("指令 %s seq=%d 重试 %d 次仍未到位，停止重试转人工（设备卡死/失联）",
                                    pending.getCommandAction(), pending.getCommandSeq(), pending.getRetryCount()));
                } else {
                    log.info("重试超限定格未命中（流水已被事件推进） deviceNo={} seq={}",
                            pending.getDeviceNo(), pending.getCommandSeq());
                }
            } else {
                //重发同 seq：设备已执行过则幂等忽略（事件丢失场景），真没收到则执行（指令丢失场景）
                log.info("指令超时重试 deviceNo={} action={} seq={} 已重试={}/{}",
                        pending.getDeviceNo(), pending.getCommandAction(), pending.getCommandSeq(),
                        pending.getRetryCount(), properties.getMaxRetry());
                commandService.retryPending(pending);
            }
        }

        //2. 离线扫描：心跳 TTL 过期的设备告警（raise 内置 SETNX 去重窗口，持续离线不刷屏）
        monitorService.scanOffline();
    }
}
