package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.CommandStatus;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceCommandService;
import com.reason.modules.device.service.DeviceMonitorService;
import com.reason.modules.device.service.ManualHoldService;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 升降杆超时与离线监控任务（barrierMonitorTask，Quartz 每 30 秒）
 *
 * <p>"十公里外不可靠"的平台侧兜底，两件事：
 * <ol>
 *   <li>指令超时对账（协议 v2 / T20 改造）：下发后超过阈值仍"待到位"（事件丢失/设备卡死/网络断）
 *       → <b>先发 QUERY_STATE 拿设备实况</b>（替代旧版查自己写的台账快照——上行断时快照陈旧会误判）：
 *       实况=目标态 → 设备已到位（事件丢了/外力已达目标），按 seq 精确销账 ARRIVED 不重试；
 *       实况=动作中 → 不重试不告警（动作中不抢，下轮再看）；
 *       实况=其他（含 FAULT）或查询失败 → 重试同 seq（设备幂等去重，不会重复动作），
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
    private final ManualHoldService manualHoldService;

    public BarrierMonitorTask(BarrierProperties properties,
                              DeviceCommandLogService commandLogService,
                              DeviceCommandService commandService,
                              DeviceAlarmService alarmService,
                              DeviceMonitorService monitorService,
                              DeviceRecordDao recordDao,
                              ManualHoldService manualHoldService) {
        this.properties = properties;
        this.commandLogService = commandLogService;
        this.commandService = commandService;
        this.alarmService = alarmService;
        this.monitorService = monitorService;
        this.recordDao = recordDao;
        this.manualHoldService = manualHoldService;
    }

    @Override
    public void run(String params) {
        //1. 指令超时对账：待到位且超过阈值的流水
        List<DeviceCommandLogEntity> timeouts =
                commandLogService.findTimeoutPending(properties.getCommandTimeoutSeconds());
        for (DeviceCommandLogEntity pending : timeouts) {
            //单条异常（QUERY 超时/DB 抖动等）不中断整轮：记日志后继续下一条，自愈依赖下轮扫描
            try {
                reconcileTimeout(pending);
            } catch (Exception e) {
                log.warn("超时指令处理异常，跳过本轮 deviceNo={} seq={} cause={}",
                        pending.getDeviceNo(), pending.getCommandSeq(), e.getMessage());
            }
        }

        //2. 台账卡 MOVING 巡检（0.4）：动作卡死未到位是"静默故障"——无事件无心跳异常，
        //   只能靠巡检发现。只喊不改状态（铁律）；告警确认闭环在管理端
        scanStuckMoving();

        //3. 离线扫描：心跳 TTL 过期的设备告警（raise 内置 SETNX 去重窗口，持续离线不刷屏）
        monitorService.scanOffline();
    }

    /**
     * 巡检台账卡 MOVING（0.4）：台账 MOVING 超过阈值（远超动作耗时+上报延迟）且无进展 =
     * 设备卡在动作中（到位事件丢失但设备也没再报）——显式告警交人工（只喊不改，T6）
     */
    private void scanStuckMoving() {
        long threshold = System.currentTimeMillis() / 1000 - properties.getMovingStuckSeconds();
        List<DeviceRecordEntity> stuck = recordDao.selectList(new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceState, DeviceState.MOVING.getCode())
                .lt(DeviceRecordEntity::getDeviceUpdatetime, threshold));
        for (DeviceRecordEntity record : stuck) {
            alarmService.raise(record.getDeviceNo(), AlarmType.MOVING_STUCK,
                    "台账卡动作中(MOVING)超过 " + properties.getMovingStuckSeconds()
                            + "s 未到位，疑似机械卡死/事件丢失——需人工现场确认");
            log.warn("巡检发现动作卡死未到位 deviceNo={} 卡MOVING {}+s（告警交人工）",
                    record.getDeviceNo(), properties.getMovingStuckSeconds());
        }
    }

    /**
     * 单条超时指令对账（协议 v2 / T20：先拿设备实况再决策，决策不依赖平台自己的台账快照）
     */
    private void reconcileTimeout(DeviceCommandLogEntity pending) {
        String deviceNo = pending.getDeviceNo();
        String action = pending.getCommandAction();
        int targetState = "OPEN".equals(action)
                ? DeviceState.UP.getCode() : DeviceState.DOWN.getCode();

        //1. 主动查询设备实况（QUERY_STATE）——上行断时事件/心跳都不可信，唯一可靠窗口是主动问
        DeviceCommandService.QueryResult qr = commandService.queryState(deviceNo);
        if (qr == null || qr.getState() == null) {
            //查询失败（下行也不可达/设备无应答）：无实况可依，走重试/超限路径
            log.warn("超时指令对账：状态查询失败 deviceNo={} seq={} -> 按重试路径处理", deviceNo, pending.getCommandSeq());
            retryOrExceed(pending);
            return;
        }

        int actual = qr.getState();
        if (actual == targetState) {
            //设备实况已到位（到位事件丢失但动作实际完成 / 外力已达目标态）：
            //按 seq 精确销账 ARRIVED——证据是设备实况，不重试不误告警
            commandLogService.markArrivedBySeq(deviceNo, pending.getCommandSeq(), action);
            log.info("超时指令经状态查询确认已到位，按 seq 销账 deviceNo={} action={} seq={}（事件丢失，QUERY_STATE 兜底）",
                    deviceNo, action, pending.getCommandSeq());
            return;
        }
        if (actual == DeviceState.MOVING.getCode()) {
            //动作仍在进行（长动作/慢链路）：不重试不告警（动作中不抢），下轮再看
            log.debug("超时指令对账：设备仍动作中，本轮不干预 deviceNo={} seq={}", deviceNo, pending.getCommandSeq());
            return;
        }
        //实况非目标态且非动作中（含 FAULT 与其他稳定态背离）：重试或超限（FAULT 收口归 0.6）
        retryOrExceed(pending);
    }

    /**
     * 重试分层（0.2 增补仲裁）：保持期内不重试（人在接管，Monitor 通道与 AutoTask 同规则让位）；
     * 旧代际指令（存在更晚 seq 的流水）被新指令取代 -> SUPERSEDED 终止挂账；
     * 其余：未达上限重发同 seq；达上限定格 RETRY_EXCEEDED + 告警转人工
     */
    private void retryOrExceed(DeviceCommandLogEntity pending) {
        //仲裁：手动保持期内（人在接管）——超时流水可能是被手动操作取代的旧指令，不重试只挂账
        if (manualHoldService.isActive(pending.getDeviceNo())) {
            log.info("手动保持期内，超时指令不重试 deviceNo={} seq={}（下轮保持期过后再看）",
                    pending.getDeviceNo(), pending.getCommandSeq());
            return;
        }
        //代际裁决（0.2）：同设备存在更晚 seq 的流水（无论何状态）→ 本指令已被新指令取代，永不会执行
        DeviceCommandLogEntity newer = commandLogService.getOne(new LambdaQueryWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getDeviceNo, pending.getDeviceNo())
                .gt(DeviceCommandLogEntity::getCommandSeq, pending.getCommandSeq())
                .orderByDesc(DeviceCommandLogEntity::getCommandSeq)
                .last("LIMIT 1"));
        if (newer != null) {
            boolean superseded = commandLogService.markSuperseded(pending.getCommandId());
            if (superseded) {
                log.warn("超时指令被更新指令取代 -> SUPERSEDED deviceNo={} seq={}（新代际 seq={} 已接管）",
                        pending.getDeviceNo(), pending.getCommandSeq(), newer.getCommandSeq());
            }
            return;
        }
        //保持期让位 + 代际裁决之后：本指令仍是当前代际，走重试分层
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
}
