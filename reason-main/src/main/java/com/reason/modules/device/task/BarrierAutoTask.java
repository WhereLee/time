package com.reason.modules.device.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.BarrierTimeRule;
import com.reason.modules.device.service.DeviceCommandService;
import com.reason.modules.device.service.DeviceMonitorService;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * 升降杆自动规则对账任务（barrierAutoTask，Quartz 每分钟）
 *
 * <p>采用"周期对账"而非"准点触发"（a+b 方案取 b）：每轮只问一个问题——
 * "此刻应然（时间规则）vs 当前实然（台账快照）"，不一致就校正。这一个动作统一治了四个边界：
 * <ul>
 *   <li>准点触发错过（重启/宕机/misfire DoNothing 不补跑）→ 下一轮对账自然补上；</li>
 *   <li>时钟漂移 → 对账不信任任何一次触发结果，只信当前比对；</li>
 *   <li>规则变更 → 改配置后下一轮即按新规则校正，无需迁移；</li>
 *   <li>断电恢复/外力改态 → 设备重新上线后心跳对账纠正台账，本任务再纠正杆。</li>
 * </ul>
 * 准点性代价：误差 ≤ 1 轮（分钟级），对"白天放行"场景足够。</p>
 *
 * <p>仲裁顺序（每道闸都有明确理由）：手动保持期让位 → 离线不打扰 → 动作中不抢 →
 * 故障停自动 → 应然=实然跳过 → 下发校正。</p>
 */
@Slf4j
@Component("barrierAutoTask")
public class BarrierAutoTask implements ITask {

    private final BarrierProperties properties;
    private final BarrierTimeRule timeRule;
    private final DeviceRecordDao recordDao;
    private final DeviceMonitorService monitorService;
    private final DeviceCommandService commandService;
    private final StringRedisTemplate stringRedisTemplate;

    public BarrierAutoTask(BarrierProperties properties,
                           BarrierTimeRule timeRule,
                           DeviceRecordDao recordDao,
                           DeviceMonitorService monitorService,
                           DeviceCommandService commandService,
                           StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.timeRule = timeRule;
        this.recordDao = recordDao;
        this.monitorService = monitorService;
        this.commandService = commandService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(String params) {
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
     * 单台设备对账：四道仲裁闸 + 应然/实然比对 + 校正下发
     */
    private void reconcileOne(DeviceRecordEntity record, int desired, String action) {
        String deviceNo = record.getDeviceNo();
        int actual = record.getDeviceState();

        //仲裁1：手动保持期——人在接管（@ManualHold 写的 Redis key），自动规则让位
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(BarrierRedisKeys.MANUAL_HOLD_PREFIX + deviceNo))) {
            log.debug("手动保持期内，自动规则让位 deviceNo={}", deviceNo);
            return;
        }
        //仲裁2：离线设备不下发——发了也是 SEND_FAILED 白记账；等它上线，心跳对账+下轮校正自然接管
        if (!monitorService.isOnline(deviceNo)) {
            return;
        }
        //仲裁3：动作中不抢——非瞬时动作，等它做完（下轮再看，最多多等一分钟）
        if (actual == DeviceState.MOVING.getCode()) {
            return;
        }
        //仲裁4：故障停自动——FAULT 交人工处置，自动重试只会反复撞同一堵墙（"停自动"是故障机制的一部分）
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
}
