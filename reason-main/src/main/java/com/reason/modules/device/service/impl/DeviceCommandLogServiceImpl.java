package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
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
import org.springframework.stereotype.Service;

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

    public DeviceCommandLogServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 启动播种：Redis 数据丢失（FLUSHDB/无持久化重启）后 INCR 从 1 重来，
     * 新 seq 会撞 device_command_log 的历史行唯一索引 u_device_seq——
     * 把每台设备的 seq 播种为 DB 历史最大值（setIfAbsent：Redis 健在则不覆盖，
     * 丢了才播种），下次 INCR 从 max+1 继续，单调性跨 Redis 数据丢失仍成立。
     *
     * <p>边界：仅处理 key 缺失（全量丢失），不处理 key 落后（旧 RDB 快照部分回退，
     * 需 GET+比对+SET 取较大者，边缘场景此处不扩展）；播种失败不阻断启动
     * （尽力恢复而非启动强依赖，失败时首次下发撞唯一索引仍有显式报错兜底）。</p>
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
                    Boolean seeded = stringRedisTemplate.opsForValue().setIfAbsent(
                            BarrierRedisKeys.CMD_SEQ_PREFIX + deviceNo, String.valueOf(maxSeq));
                    if (Boolean.TRUE.equals(seeded)) {
                        log.info("指令序号播种 deviceNo={} seq={}(DB 历史最大值，Redis 数据丢失恢复)", deviceNo, maxSeq);
                    }
                }
            }
        } catch (Exception e) {
            //播种是尽力恢复不是启动强依赖：表未建/DB 抖动都不应拖垮整个应用上下文
            log.error("指令序号播种失败（不阻断启动；Redis 数据丢失场景下首次下发可能撞唯一索引）", e);
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
    public DeviceCommandLogEntity recordPending(String deviceNo, String action, long seq, TriggerType trigger) {
        DeviceCommandLogEntity entity = new DeviceCommandLogEntity();
        entity.setDeviceNo(deviceNo);
        entity.setCommandAction(action);
        entity.setCommandSeq(seq);
        entity.setTriggerType(trigger.getCode());
        entity.setCommandStatus(CommandStatus.PENDING.getCode());
        entity.setRetryCount(0);
        long now = System.currentTimeMillis() / 1000;
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
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis() / 1000));
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
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis() / 1000));
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
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis() / 1000));
        if (updated) {
            log.warn("设备故障按 seq 归属中断指令 deviceNo={} seq={}", deviceNo, seq);
        }
        return updated;
    }

    @Override
    public List<DeviceCommandLogEntity> findTimeoutPending(int timeoutSeconds) {
        long deadline = System.currentTimeMillis() / 1000 - timeoutSeconds;
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
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis() / 1000));
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
        int pageNum = form.getPage() == null ? 1 : Integer.parseInt(form.getPage());
        int limit = form.getLimit() == null ? 10 : Integer.parseInt(form.getLimit());

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
                .set(DeviceCommandLogEntity::getCommandUpdatetime, System.currentTimeMillis() / 1000));
    }
}
