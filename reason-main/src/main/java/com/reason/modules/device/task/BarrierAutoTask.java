package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.filter.TraceIdFilter;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final StringRedisTemplate stringRedisTemplate;

    public BarrierAutoTask(BarrierProperties properties,
                           BarrierTimeRule timeRule,
                           DeviceRecordDao recordDao,
                           DeviceMonitorService monitorService,
                           DeviceCommandService commandService,
                           DeviceCommandLogService commandLogService,
                           DeviceAlarmService alarmService,
                           ManualHoldService manualHoldService,
                           StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.timeRule = timeRule;
        this.recordDao = recordDao;
        this.monitorService = monitorService;
        this.commandService = commandService;
        this.commandLogService = commandLogService;
        this.alarmService = alarmService;
        this.manualHoldService = manualHoldService;
        this.stringRedisTemplate = stringRedisTemplate;
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
            //记日志后继续下一台，本台留待下轮自然重试（周期对账的自愈性）；
            //批次4：连续异常计数升级告警（单轮 WARN 无人可见——批次3 Redis 事故每轮 50 条 WARN 的教训）
            try {
                reconcileOne(record, desired, action);
                //成功轮：清连续异常计数 + 关闭未处理升级告警（谁开谁关）
                clearFailStreak(record.getDeviceNo());
            } catch (Exception e) {
                log.warn("自动对账处理单台设备异常，跳过 deviceNo={} cause={}",
                        record.getDeviceNo(), e.getMessage());
                //升级告警是附属副作用，绝不该反噬对账主循环（P4 剧本暴露：content 超列长在 raise
                //内部炸出——整轮对账中断进 schedule_job_log 失败记录）——双层防御，次级异常仅记日志
                try {
                    escalateIfFailing(record.getDeviceNo(), e);
                } catch (Exception escalateEx) {
                    log.warn("对账异常升级处理失败（不影响主循环）deviceNo={} cause={}",
                            record.getDeviceNo(), escalateEx.getMessage());
                }
            }
        }
    }

    /**
     * 对账连续异常升级（批次4）：单台异常计数（Redis INCR，TTL 1h），连续 N 轮 -> 升级显式告警。
     * 语义=严格连续：任一轮处理成功即清零（抓"持续坏"不抓"偶发抖"——Redis/DB 单发抖动
     * 不该积累成告警；持续故障如 seq 撞唯一索引会每轮必炸，N 轮内必然触发）
     */
    private void escalateIfFailing(String deviceNo, Exception cause) {
        String key = BarrierRedisKeys.RECONCILE_FAIL_PREFIX + deviceNo;
        Long streak;
        try {
            streak = stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
        } catch (RuntimeException redisEx) {
            //计数不可用（Redis 故障）：降级仅记 WARN，本次不升级（下轮再说）
            log.warn("对账异常计数失败 deviceNo={} cause={}", deviceNo, redisEx.getMessage());
            return;
        }
        if (streak != null && streak >= properties.getReconcileFailStreakThreshold()) {
            alarmService.raise(deviceNo, AlarmType.RECONCILE_ERROR,
                    String.format("对账连续 %d 轮处理异常（最近：%s）——持续故障而非抖动，检查 seq/DB/Redis",
                            streak, brief(cause)));
        }
    }

    /**
     * 异常摘要截断（P4 剧本暴露）：MyBatis 撞唯一索引的异常 message 是完整 SQL 文本（数千字符），
     * 直塞 alarm_content(512) 会 Data too long——截断到安全长度（对告警人可读性无损失）
     */
    private String brief(Exception cause) {
        String msg = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return msg.length() <= 200 ? msg : msg.substring(0, 200);
    }

    /**
     * 对账处理成功：清连续异常计数 + 关闭未处理升级告警。
     * 计数未清掉就返回（不关告警）——避免"计数残留 + 告警已关"错位，下轮成功再收尾
     */
    private void clearFailStreak(String deviceNo) {
        try {
            stringRedisTemplate.delete(BarrierRedisKeys.RECONCILE_FAIL_PREFIX + deviceNo);
        } catch (RuntimeException e) {
            log.warn("对账异常计数清理失败 deviceNo={} cause={}", deviceNo, e.getMessage());
            return;
        }
        alarmService.closeUnhandledAlarm(deviceNo, AlarmType.RECONCILE_ERROR);
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
        //仲裁2：离线设备直接跳过（批次4 顺序调整）：离线时既发不了指令也无从评估熔断——
        //熔断判据只对"在线且校正失败"有意义；离线由心跳/离线告警负责（离线设备进熔断
        //判定会每窗口 raise 校正失败告警——与离线告警重复喊话，且离线设备永远无法靠
        //下发产生新 ARRIVED 解除熔断——批次4 剧本发现）
        if (!monitorService.isOnline(deviceNo)) {
            return;
        }
        //熔断维护（0.3）：该设备是否存在"超龄未闭环"流水（上轮校正下发后既没到位也没被 monitor 终结）
        if (!maintainFailStreak(deviceNo, record, desired)) {
            return; //本轮已熔断跳过
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
    private boolean maintainFailStreak(String deviceNo, DeviceRecordEntity record, int desired) {
        int threshold = properties.getAutoFailStreakThreshold();
        List<DeviceCommandLogEntity> recent = commandLogService.list(new LambdaQueryWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getDeviceNo, deviceNo)
                .eq(DeviceCommandLogEntity::getTriggerType, com.reason.modules.device.enums.TriggerType.AUTO_RULE.getCode())
                .orderByDesc(DeviceCommandLogEntity::getCommandSeq)
                .last("LIMIT " + threshold));
        if (threshold <= 0 || recent.size() < threshold) {
            //历史不足 N 条自动校正：无从判"连续失败"，放行；阈值<=0（错位配置）同样放行——
            //否则空窗口 allMatch=true 会误走熔断分支且 recent.get(0) 越界（批次4 单测暴露）
            return true;
        }
        boolean allFailed = recent.stream()
                .allMatch(l -> l.getCommandStatus() != CommandStatus.ARRIVED.getCode());
        if (allFailed) {
            //批次4 恢复标记完善（剧本发现）：设备已到位（实然=应然）却仍挂历史失败窗口——静默恢复场景
            //（外力到位/心跳对账归位后不再下发，窗口永远翻不了新）——熔断已无意义：放行（后续
            //应然=实然直接 return，无下发风险），并关闭残留校正失败告警（归位即恢复，停止周期重复喊话）
            if (record.getDeviceState() == desired) {
                alarmService.closeUnhandledAlarm(deviceNo, AlarmType.AUTO_CORRECT_FAILED);
                return true;
            }
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
