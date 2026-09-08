package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.filter.TraceIdFilter;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.CommandStatus;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.BarrierTimeRule;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceCommandService;
import com.reason.modules.device.service.DeviceMonitorService;
import com.reason.modules.device.service.ManualHoldService;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * 升降杆自动规则对账任务（barrierAutoTask，Quartz 每分钟）
 *
 * <p>采用"周期对账"而非"准点触发"（a+b 方案取 b）：每轮只问一个问题——
 * "此刻应然（时间规则）vs 当前实然（台账快照）"，不一致就校正。这一个动作统一治了四个边界：
 * 准点触发错过/时钟漂移/规则变更/断电恢复——对账不信任任何单次触发结果，只信当前比对。
 * 准点性代价：误差 ≤ 1 轮（分钟级），对"白天放行"场景足够。</p>
 *
 * <p>仲裁顺序（每道闸都有明确理由）：手动保持期让位（0.2 收口 ManualHoldService，
 * Redis+DB 双写）→ 熔断（0.3：平台观测连续未闭环则停自动，不无限轰杆）→ 离线不打扰 →
 * 动作中不抢 → 故障停自动 → 应然=实然跳过 → 下发校正。</p>
 */
@Slf4j
@Component("barrierAutoTask")
public class BarrierAutoTask implements ITask {

    private final BarrierProperties properties;
    private final BarrierTimeRule timeRule;
    private final DeviceRecordDao recordDao;
    private final DeviceMonitorService monitorService;
    private final DeviceCommandService commandService;
    private final DeviceCommandLogService commandLogService;
    private final DeviceAlarmService alarmService;
    private final ManualHoldService manualHoldService;

    public BarrierAutoTask(BarrierProperties properties,
                           BarrierTimeRule timeRule,
                           DeviceRecordDao recordDao,
                           DeviceMonitorService monitorService,
                           DeviceCommandService commandService,
                           DeviceCommandLogService commandLogService,
                           DeviceAlarmService alarmService,
                           ManualHoldService manualHoldService) {
        this.properties = properties;
        this.timeRule = timeRule;
        this.recordDao = recordDao;
        this.monitorService = monitorService;
        this.commandService = commandService;
        this.commandLogService = commandLogService;
        this.alarmService = alarmService;
        this.manualHoldService = manualHoldService;
    }

    @Override
    public void run(String params) {
        //批次1 链路号：Quartz 任务线程无 HTTP 上下文，本轮生成 traceId——本轮全部日志与
        //下发指令共用（sim 执行/事件上报沿用同号，自动升降链路可 grep 串联）
        MDC.put(TraceIdFilter.MDC_KEY, TraceIdFilter.generateTraceId());
        try {
            doRun(params);
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }

    private void doRun(String params) {
        if (!properties.isAutoEnabled()) {
            log.debug("自动升降已关闭(reason.barrier.auto-enabled=false)，本轮跳过");
            return;
        }

        //应然状态：问时间规则（规则不碰设备不碰库，纯判断）
        int desired = timeRule.desiredState(LocalTime.now());
        String action = desired == DeviceState.UP.getCode() ? "OPEN" : "CLOSE";

        //只对"接入过"的设备自动（未接入=从没上过线，档案刚建，等它第一次心跳再说）
        List<DeviceRecordEntity> records = recordDao.selectList(new LambdaQueryWrapper<DeviceRecordEntity>()
                .ne(DeviceRecordEntity::getDeviceState, DeviceState.NOT_CONNECTED.getCode()));

        for (DeviceRecordEntity record : records) {
            //单台设备异常（Redis/DB 抖动、seq 生成失败等）不中断整轮对账：
            //记日志后继续下一台，本台留待下轮自然重试（周期对账的自愈性）
            try {
                reconcileOne(record, desired, action);
            } catch (Exception e) {
                log.warn("自动对账处理单台设备异常，跳过 deviceNo={} cause={}",
                        record.getDeviceNo(), e.getMessage());
            }
        }
    }

    /**
     * 单台设备对账：仲裁闸 + 熔断计数 + 应然/实然比对 + 校正下发
     */
    private void reconcileOne(DeviceRecordEntity record, int desired, String action) {
        String deviceNo = record.getDeviceNo();
        int actual = record.getDeviceState();

        //仲裁1：手动保持期——人在接管（Redis+DB 双写判定），自动规则让位
        if (manualHoldService.isActive(deviceNo)) {
            log.debug("手动保持期内，自动规则让位 deviceNo={}", deviceNo);
            return;
        }
        //熔断维护（0.3）：该设备是否存在"超龄未闭环"流水（上轮校正下发后既没到位也没被 monitor 终结）
        if (!maintainFailStreak(deviceNo, record)) {
            return; //本轮已熔断跳过
        }
        //仲裁2：离线设备不下发——发了也是 SEND_FAILED 白记账；等它上线，心跳对账+下轮校正自然接管
        if (!monitorService.isOnline(deviceNo)) {
            return;
        }
        //仲裁3：动作中不抢——非瞬时动作，等它做完（下轮再看，最多多等一分钟）
        if (actual == DeviceState.MOVING.getCode()) {
            return;
        }
        //仲裁4：故障停自动——FAULT 交人工处置，自动重试只会反复撞同一堵墙
        if (actual == DeviceState.FAULT.getCode()) {
            return;
        }
        //对账：应然 = 实然则无事发生（绝大多数轮次走到这里结束——对账的常态是"确认没事"）
        if (actual == desired) {
            return;
        }

        log.info("自动对账发现偏差 deviceNo={} 实然={} 应然={} -> 下发校正指令 {}",
                deviceNo, actual, desired, action);
        commandService.sendByRule(deviceNo, action);
    }

    /**
     * 熔断裁决（0.3）：该设备最近 N 条自动校正流水（trigger=AUTO_RULE）是否全部未闭环
     * （PENDING/SEND_FAILED/SUPERSEDED/EXEC_FAILED 都算——含被 monitor 代际裁决取代的），
     * 全部未闭环 = 平台观测连续失败 -> 熔断停自动 + 告警。
     * 判据用"流水窗口"而非"最新一条超龄"：monitor 的代际裁决会把旧指令标 SUPERSEDED 并让
     * 最新一条保持年轻，只看最新一条会永远不超龄（熔断与代际裁决互踩）；窗口判据两者自洽。
     * 熔断持续到窗口内出现 ARRIVED（人工复位/恢复后自动校正成功）自动解除
     *
     * @return false=已熔断（本轮跳过不下发）
     */
    private boolean maintainFailStreak(String deviceNo, DeviceRecordEntity record) {
        int threshold = properties.getAutoFailStreakThreshold();
        List<DeviceCommandLogEntity> recent = commandLogService.list(new LambdaQueryWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getDeviceNo, deviceNo)
                .eq(DeviceCommandLogEntity::getTriggerType, com.reason.modules.device.enums.TriggerType.AUTO_RULE.getCode())
                .orderByDesc(DeviceCommandLogEntity::getCommandSeq)
                .last("LIMIT " + threshold));
        if (recent.size() < threshold) {
            //历史不足 N 条自动校正：无从判"连续失败"，放行
            return true;
        }
        boolean allFailed = recent.stream()
                .allMatch(l -> l.getCommandStatus() != CommandStatus.ARRIVED.getCode());
        if (allFailed) {
            log.warn("自动校正连续 {} 条未闭环 -> 熔断停自动 deviceNo={}（最近 seq={} 未到位）",
                    threshold, deviceNo, recent.get(0).getCommandSeq());
            alarmService.raise(deviceNo, AlarmType.AUTO_CORRECT_FAILED,
                    String.format("自动校正连续 %d 条未闭环(最近 seq=%d)，停自动交人工——疑似到位传感器失效/半断电/主控失聪",
                            threshold, recent.get(0).getCommandSeq()));
            return false;
        }
        return true;
    }
}
