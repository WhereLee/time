package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.PageParams;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.StringUtils;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceCommandLogDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.enums.CommandStatus;
import com.reason.modules.device.enums.TriggerType;
import com.reason.modules.device.form.DeviceCommandLogForm;
import com.reason.modules.device.service.DeviceCommandLogService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 设备指令流水服务实现
 *
 * <p>seq 生成用 Redis INCR（barrier:cmd-seq:{deviceNo}）：原子递增、跨重启持久、
 * 多实例平台共享同一序列——DB MAX+1 在并发下发时会撞唯一索引 u_device_seq。
 * 流水状态推进全部走 CAS 条件 UPDATE（带 from 状态守卫）：监控任务（Quartz 线程）
 * 与设备事件（HTTP 线程）对同一条流水存在竞态，无守卫的覆盖写会把已 ARRIVED 的
 * 终态误改成 RETRY_EXCEEDED/SEND_FAILED——CAS 未命中即说明已被其他路径推进，放弃即可。</p>
 */
@Slf4j
@Service("deviceCommandLogService")
public class DeviceCommandLogServiceImpl extends ServiceImpl<DeviceCommandLogDao, DeviceCommandLogEntity>
        implements DeviceCommandLogService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 启动对齐脚本（批次4 升级）：GET 现值比对 DB MAX，仅当 key 缺失或现值更小时 SET——
     * LUA 原子（多实例并发启动不会交错覆盖），返回 1=已校正 / 0=无需动
     */
    private static final RedisScript<Long> ALIGN_SEQ_SCRIPT = new DefaultRedisScript<>(
            "local cur = redis.call('GET', KEYS[1]) "
                    + "if not cur or tonumber(cur) < tonumber(ARGV[1]) then "
                    + "redis.call('SET', KEYS[1], ARGV[1]) "
                    + "return 1 end "
                    + "return 0",
            Long.class);

    public DeviceCommandLogServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 启动播种/对齐：Redis 数据丢失（FLUSHDB/无持久化重启）后 INCR 从 1 重来，
     * 新 seq 会撞 device_command_log 的历史行唯一索引 u_device_seq——
     * 把每台设备的 seq 对齐为 max(Redis 现值, DB 历史最大值)：健在且领先则不动，
     * key 缺失或落后则校正（旧 RDB 快照回退——批次3 实测事故：手动启动 Redis 加载旧 dump，
     * seq 计数器回退而 DB MAX 更高，对账每轮 50 台撞唯一索引静默跳过），
     * 单调性跨 Redis 数据丢失/回退均成立。
     *
     * <p>边界：仅启动时对齐（运行时回退的可见性由对账连续异常升级告警 RECONCILE_ERROR 覆盖）；
     * 对齐失败不阻断启动（尽力恢复而非启动强依赖，失败时下发撞唯一索引仍有显式报错兜底）。</p>
     */
    @PostConstruct
    public void seedSeqFromDb() {
        try {
            List<Map<String, Object>> rows = baseMapper.selectMaps(new QueryWrapper<DeviceCommandLogEntity>()
                    .select("device_no", "MAX(command_seq) AS max_seq")
                    .groupBy("device_no"));
            for (Map<String, Object> row : rows) {
                Object deviceNo = row.get("device_no");
                Object maxSeq = row.get("max_seq");
                if (deviceNo != null && maxSeq != null) {
                    //对齐取大（LUA 原子）：DB MAX 更大才 SET，Redis 现值领先则保留
                    Long aligned = stringRedisTemplate.execute(ALIGN_SEQ_SCRIPT,
                            Collections.singletonList(BarrierRedisKeys.CMD_SEQ_PREFIX + deviceNo),
                            String.valueOf(maxSeq));
                    if (aligned != null && aligned == 1L) {
                        log.info("指令序号对齐 deviceNo={} seq={}(DB 历史最大值；Redis 缺失或落后已校正)",
                                deviceNo, maxSeq);
                    }
                }
            }
        } catch (Exception e) {
            //对齐是尽力恢复不是启动强依赖：表未建/DB 抖动都不应拖垮整个应用上下文
            log.error("指令序号对齐失败（不阻断启动；Redis 数据丢失/回退场景下首次下发可能撞唯一索引）", e);
        }
    }

    @Override
    public long nextSeq(String deviceNo) {
        Long seq = stringRedisTemplate.opsForValue().increment(BarrierRedisKeys.CMD_SEQ_PREFIX + deviceNo);
        if (seq == null) {
            //Redis 不可用时指令序号无法保证唯一——快速失败，宁可发不出去也不能发重
            throw new RRException("指令序号生成失败(Redis 不可用): " + deviceNo);
        }
        return seq;
    }

    @Override
    public long countPending() {
        //批次4 指标：待到位流水数（积压观测——通道故障的第一指标）
        return this.count(new LambdaQueryWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getCommandStatus, CommandStatus.PENDING.getCode()));
    }

    @Override
    public DeviceCommandLogEntity recordPending(String deviceNo, String action, long seq, TriggerType trigger) {
        DeviceCommandLogEntity entity = new DeviceCommandLogEntity();
        entity.setDeviceNo(deviceNo);
        entity.setCommandAction(action);
        entity.setCommandSeq(seq);
        entity.setTriggerType(trigger.getCode());
        entity.setCommandStatus(CommandStatus.PENDING.getCode());
        entity.setRetryCount(0);
        //D-H 毫秒化：流水时间列全毫秒（与 grace/超时判据同单位）
        long now = System.currentTimeMillis();
        entity.setCommandCreatetime(now);
        entity.setCommandUpdatetime(now);
        this.save(entity);
        return entity;
    }

    @Override
    public void markSendFailed(Long commandId) {
        casStatus(commandId, CommandStatus.PENDING, CommandStatus.SEND_FAILED);
    }

    @Override
    public boolean markArrivedBySeq(String deviceNo, long seq, String action) {
        //协议 v2：事件携带 commandSeq，单条条件 UPDATE 按 (deviceNo, seq, action, PENDING) 精确闭环
        //——CAS 守卫（PENDING 才销）保证并发路径（监控重试/事件到达）不覆盖终态；
        //未命中 = 该 seq 无在途流水（已销/已终态/从未存在），记日志不告警（幂等）
        boolean arrived = this.update(new LambdaUpdateWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getDeviceNo, deviceNo)
                .eq(DeviceCommandLogEntity::getCommandSeq, seq)
                .eq(DeviceCommandLogEntity::getCommandAction, action)
                .eq(DeviceCommandLogEntity::getCommandStatus, CommandStatus.PENDING.getCode())
                .set(DeviceCommandLogEntity::getCommandStatus, CommandStatus.ARRIVED.getCode())
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis()));
        if (arrived) {
            log.info("指令按 seq 精确闭环 deviceNo={} action={} seq={}", deviceNo, action, seq);
        } else {
            log.debug("到位事件未命中在途流水(已闭环/已终态/非指令驱动) deviceNo={} action={} seq={}",
                    deviceNo, action, seq);
        }
        return arrived;
    }

    @Override
    public void markExecFailed(String deviceNo) {
        //设备故障 = 所有在途指令中断（一条 SQL 批量推进，无逐条查改）
        boolean updated = this.update(new LambdaUpdateWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getDeviceNo, deviceNo)
                .eq(DeviceCommandLogEntity::getCommandStatus, CommandStatus.PENDING.getCode())
                .set(DeviceCommandLogEntity::getCommandStatus, CommandStatus.EXEC_FAILED.getCode())
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis()));
        if (updated) {
            log.warn("设备故障中断在途指令 deviceNo={}", deviceNo);
        }
    }

    @Override
    public boolean markExecFailedBySeq(String deviceNo, long seq) {
        //0.6 按 seq 归属中断：故障事件携带 commandSeq 时精确中断该条（PENDING 守卫防覆盖终态）
        boolean updated = this.update(new LambdaUpdateWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getDeviceNo, deviceNo)
                .eq(DeviceCommandLogEntity::getCommandSeq, seq)
                .eq(DeviceCommandLogEntity::getCommandStatus, CommandStatus.PENDING.getCode())
                .set(DeviceCommandLogEntity::getCommandStatus, CommandStatus.EXEC_FAILED.getCode())
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis()));
        if (updated) {
            log.warn("设备故障按 seq 归属中断指令 deviceNo={} seq={}", deviceNo, seq);
        }
        return updated;
    }

    @Override
    public List<DeviceCommandLogEntity> findTimeoutPending(int timeoutSeconds) {
        //D-H 毫秒化：超时阈=now - timeout*1000（列已为毫秒）
        long deadline = System.currentTimeMillis() - timeoutSeconds * 1000L;
        return this.list(new LambdaQueryWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getCommandStatus, CommandStatus.PENDING.getCode())
                .lt(DeviceCommandLogEntity::getCommandCreatetime, deadline));
    }

    @Override
    public void markRetried(Long commandId) {
        //SQL 原子自增 + PENDING 守卫（已终态的流水不再计数：事件恰好到位时重试计数不应再动）
        this.update(new LambdaUpdateWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getCommandId, commandId)
                .eq(DeviceCommandLogEntity::getCommandStatus, CommandStatus.PENDING.getCode())
                .setSql("retry_count = retry_count + 1")
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis()));
    }

    @Override
    public boolean markRetryExceeded(Long commandId) {
        return casStatus(commandId, CommandStatus.PENDING, CommandStatus.RETRY_EXCEEDED);
    }

    @Override
    public boolean markSuperseded(Long commandId) {
        return casStatus(commandId, CommandStatus.PENDING, CommandStatus.SUPERSEDED);
    }

    @Override
    public PageUtils queryPage(DeviceCommandLogForm form) {
        //T15：分页参数统一钳制（非法输入不再 500、超大 limit 不放行）
        int pageNum = PageParams.page(form.getPage());
        int limit = PageParams.limit(form.getLimit());

        IPage<DeviceCommandLogEntity> page = this.page(
                new Page<>(pageNum, limit),
                new LambdaQueryWrapper<DeviceCommandLogEntity>()
                        .eq(StringUtils.isNotBlank(form.getDeviceNo()), DeviceCommandLogEntity::getDeviceNo, form.getDeviceNo())
                        .eq(StringUtils.isNotBlank(form.getCommandAction()), DeviceCommandLogEntity::getCommandAction, form.getCommandAction())
                        .eq(form.getTriggerType() != null, DeviceCommandLogEntity::getTriggerType, form.getTriggerType())
                        .eq(form.getCommandStatus() != null, DeviceCommandLogEntity::getCommandStatus, form.getCommandStatus())
                        .orderByDesc(DeviceCommandLogEntity::getCommandCreatetime)
        );
        return new PageUtils(page);
    }

    /**
     * CAS 状态跃迁：仅当流水仍为 from 状态才写入 to（并发路径已推进时未命中，不覆盖终态）
     */
    private boolean casStatus(Long commandId, CommandStatus from, CommandStatus to) {
        return this.update(new LambdaUpdateWrapper<DeviceCommandLogEntity>()
                .eq(DeviceCommandLogEntity::getCommandId, commandId)
                .eq(DeviceCommandLogEntity::getCommandStatus, from.getCode())
                .set(DeviceCommandLogEntity::getCommandStatus, to.getCode())
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis()));
    }
}
