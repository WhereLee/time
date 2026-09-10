package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.PageParams;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.StringUtils;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceAlarmDao;
import com.reason.modules.device.entity.DeviceAlarmEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.form.DeviceAlarmForm;
import com.reason.modules.device.service.DeviceAlarmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 设备告警服务实现
 *
 * <p>去重用 Redis SETNX（barrier:alarm-dedup:{deviceNo}:{type}，TTL=配置窗口）：
 * 占位成功才落库——持续异常（如设备一直离线）在窗口内只喊一次，不刷屏；
 * 窗口过期后若异常仍在，再喊一次（"还在坏"的周期性提醒）。
 * 用 DB 查重（select count）会有查-插竞态且热路径多一次查询，SETNX 原子且 O(1)。</p>
 *
 * <p>风暴限速（批次4 D-F）：去重之上的第二道闸——per-type 全局滑动窗口（ZSET，LUA 原子），
 * "真落库尝试"超上限只记日志不落库（跨设备刷屏场景：去重管不了不同设备，限速兜底）；
 * 实时批量场景由离线扫描的合并告警（BATCH_OFFLINE）先行吸收。</p>
 */
@Slf4j
@Service("deviceAlarmService")
public class DeviceAlarmServiceImpl extends ServiceImpl<DeviceAlarmDao, DeviceAlarmEntity>
        implements DeviceAlarmService {

    /**
     * 原子取删脚本（GETDEL 语义）：Redis 6.2+ 才有原生 GETDEL，本地/低版本 5.0 实测不支持
     * （G1 剧本暴露：GETDEL 报 unknown command，门控退化为每轮心跳降级 WARN+空 UPDATE）——
     * LUA 在服务端原子执行 GET 命中才 DEL，兼容 5.x 且并发心跳下不会双写
     */
    private static final RedisScript<String> GETDEL_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); if v then redis.call('DEL', KEYS[1]) end; return v",
            String.class);

    /**
     * 告警限速滑动窗口脚本（批次4 D-F）：清理滑出成员 -> 统计窗口内数量 -> 未达上限则记入并放行（0），
     * 已达上限返回 1（限速：不落库也不占位）。多命令必须原子，LUA 单请求往返
     */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local z = KEYS[1] "
                    + "local now = tonumber(ARGV[1]) "
                    + "local win = tonumber(ARGV[2]) "
                    + "local max = tonumber(ARGV[3]) "
                    + "redis.call('ZREMRANGEBYSCORE', z, 0, now - win) "
                    + "local cnt = redis.call('ZCARD', z) "
                    + "if cnt >= max then return 1 end "
                    + "redis.call('ZADD', z, now, ARGV[4]) "
                    + "redis.call('PEXPIRE', z, win * 2) "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final BarrierProperties barrierProperties;

    public DeviceAlarmServiceImpl(StringRedisTemplate stringRedisTemplate,
                                  BarrierProperties barrierProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.barrierProperties = barrierProperties;
    }

    @Override
    public void raise(String deviceNo, AlarmType type, String content) {
        //去重窗口：SETNX 占位成功 = 窗口内首次告警；失败 = 已喊过，跳过
        String dedupKey = BarrierRedisKeys.ALARM_DEDUP_PREFIX + deviceNo + ":" + type.getCode();
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1",
                barrierProperties.getAlarmDedupSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            log.debug("告警去重跳过 deviceNo={} type={}（窗口 {}s 内已告警）",
                    deviceNo, type, barrierProperties.getAlarmDedupSeconds());
            return;
        }

        //DB 时间窗查重兜底（0.8）：Redis 丢失后 SETNX 恒成功会刷屏——落库前查最近同类未处理告警，
        //存在则回滚占位跳过（告警频率低，一次 SELECT 可接受；Redis 正常时此处几乎永不命中）
        long now = System.currentTimeMillis() / 1000;
        Long dup = baseMapper.selectCount(new LambdaQueryWrapper<DeviceAlarmEntity>()
                .eq(DeviceAlarmEntity::getDeviceNo, deviceNo)
                .eq(DeviceAlarmEntity::getAlarmType, type.getCode())
                .gt(DeviceAlarmEntity::getAlarmCreatetime, now - barrierProperties.getAlarmDedupSeconds()));
        if (dup != null && dup > 0) {
            stringRedisTemplate.delete(dedupKey);
            log.debug("告警 DB 时间窗查重兜底命中，跳过 deviceNo={} type={}（Redis 去重键丢失场景）", deviceNo, type);
            return;
        }

        //批次4 D-F 限速闸（去重之后）：只有"真落库尝试"才计数——窗口内同类超上限只记日志（降级为日志的
        //告警仍可见，不静默）；同时回滚去重占位，保持"限速/去重"两机制正交（否则占位会把限速窗口
        //封满到去重窗口，双重封印超预期）
        if (isRateLimited(type)) {
            stringRedisTemplate.delete(dedupKey);
            log.warn("告警限速跳过(只记日志不落库) deviceNo={} type={}（窗口 {}s 内同类告警超 {} 条）content={}",
                    deviceNo, type, barrierProperties.getAlarmRateWindowSeconds(),
                    barrierProperties.getAlarmRateMaxPerWindow(), content);
            return;
        }

        DeviceAlarmEntity alarm = new DeviceAlarmEntity();
        alarm.setDeviceNo(deviceNo);
        alarm.setAlarmType(type.getCode());
        alarm.setAlarmContent(content);
        alarm.setAlarmHandled(0);
        alarm.setAlarmCreatetime(now);
        try {
            this.save(alarm);
        } catch (RuntimeException e) {
            //落库失败回滚去重占位：否则占位已生效，整个去重窗口内该告警被静默吞掉
            //（占位先于落库的补偿：告警无周期任务自愈，占位空窗不可接受）
            stringRedisTemplate.delete(dedupKey);
            throw e;
        }
        //批次2 B5：OFFLINE 落库成功置"未处理标记"（无 TTL 显式生命周期）——markOnlineRecovered 据此
        //GETDEL 门控：正常设备心跳恢复路径零空 UPDATE（心跳洪峰下 50 台 × 10s 的写放大归零）
        if (type == AlarmType.OFFLINE) {
            stringRedisTemplate.opsForValue().set(BarrierRedisKeys.ALARM_OPEN_PREFIX + deviceNo + ":" + type.getCode(), "1");
        }
        //告警是"喊给人听"的：warn 级日志进 error/warn 文件，运维侧可接日志告警渠道
        log.warn("设备告警 deviceNo={} type={} content={}", deviceNo, type.getDesc(), content);
    }

    /**
     * 限速判定（批次4 D-F）：per-type 全局滑动窗口。true=超限（只记日志不落库）。
     * Redis 异常降级放行——告警是"喊给人听"：宁可多喊不可漏喊（与恢复路径"宁可空写不可漏关"对偶）
     */
    private boolean isRateLimited(AlarmType type) {
        try {
            Long limited = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                    Collections.singletonList(BarrierRedisKeys.ALARM_RATE_PREFIX + type.getCode()),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(barrierProperties.getAlarmRateWindowSeconds() * 1000L),
                    String.valueOf(barrierProperties.getAlarmRateMaxPerWindow()),
                    String.valueOf(System.nanoTime()));
            return limited != null && limited == 1L;
        } catch (RuntimeException e) {
            log.warn("告警限速判定异常，降级放行 type={} cause={}", type, e.getMessage());
            return false;
        }
    }

    @Override
    public void handle(Long alarmId, Long userId) {
        //CAS 0->1：已处理/不存在的告警重复确认直接拒绝（幂等由 CAS 保证，无查改竞态）
        boolean updated = this.update(new LambdaUpdateWrapper<DeviceAlarmEntity>()
                .eq(DeviceAlarmEntity::getAlarmId, alarmId)
                .eq(DeviceAlarmEntity::getAlarmHandled, 0)
                .set(DeviceAlarmEntity::getAlarmHandled, 1)
                .set(DeviceAlarmEntity::getAlarmHandler, userId)
                .set(DeviceAlarmEntity::getAlarmHandledTime, System.currentTimeMillis() / 1000));
        if (!updated) {
            throw new RRException("告警不存在或已处理: " + alarmId);
        }
        log.info("告警已确认处理 alarmId={} userId={}", alarmId, userId);
    }

    @Override
    public int markRecovered(String deviceNo) {
        //设备状态恢复（到位事件到达）→ 状态类未处理告警自动关闭：离线/动作卡死/自动校正失败
        //（DEVICE_FAULT 不自动关：故障需人工复位确认——语义上 FAULT 的处置是检修动作不是状态事件）
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<DeviceAlarmEntity>()
                .eq(DeviceAlarmEntity::getDeviceNo, deviceNo)
                .eq(DeviceAlarmEntity::getAlarmHandled, 0)
                .in(DeviceAlarmEntity::getAlarmType,
                        AlarmType.OFFLINE.getCode(),
                        AlarmType.MOVING_STUCK.getCode(),
                        AlarmType.AUTO_CORRECT_FAILED.getCode())
                .set(DeviceAlarmEntity::getAlarmHandled, 1)
                .set(DeviceAlarmEntity::getAlarmHandledTime, System.currentTimeMillis() / 1000));
        if (rows > 0) {
            log.info("设备状态恢复，自动关闭状态类告警 {} 条 deviceNo={}", rows, deviceNo);
        }
        return rows;
    }

    @Override
    public int markOnlineRecovered(String deviceNo) {
        //只关 OFFLINE（心跳恢复 = 离线判定反转的权威）；MOVING_STUCK/AUTO_CORRECT_FAILED
        //需事件级证据，仍由 markRecovered 在事件路径关闭——类型集合分离，语义不混。
        //批次2 B5 门控：先 GETDEL 未处理标记（原子取删）——正常设备（从未 OFFLINE 告警过）心跳
        //恢复路径零 DB 写（心跳洪峰 5rps 下不再每 10s 一次空 UPDATE）；
        //人工 handle 后标记残留由本次 GETDEL 一次性吸收（UPDATE 条件 alarm_handled=0 兜底，幂等无害）；
        //markRecovered（事件路径，低频）不加门控——事件到达本身即"刚变更过状态"，写放大可接受
        String openKey = BarrierRedisKeys.ALARM_OPEN_PREFIX + deviceNo + ":" + AlarmType.OFFLINE.getCode();
        boolean marked;
        try {
            //LUA 原子取删（Redis 5.0 无 GETDEL 命令，脚本兼容——见类头 GETDEL_SCRIPT 注释）
            marked = stringRedisTemplate.execute(GETDEL_SCRIPT, Collections.singletonList(openKey)) != null;
        } catch (RuntimeException e) {
            //Redis 故障降级保底：按命中处理（执行 UPDATE）——宁可空写不可漏关（退化=批次1 现状行为）
            log.warn("OFFLINE 恢复标记 GETDEL 异常，降级为直接执行关闭 UPDATE deviceNo={} cause={}", deviceNo, e.getMessage());
            marked = true;
        }
        if (!marked) {
            return 0;
        }
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<DeviceAlarmEntity>()
                .eq(DeviceAlarmEntity::getDeviceNo, deviceNo)
                .eq(DeviceAlarmEntity::getAlarmHandled, 0)
                .eq(DeviceAlarmEntity::getAlarmType, AlarmType.OFFLINE.getCode())
                .set(DeviceAlarmEntity::getAlarmHandled, 1)
                .set(DeviceAlarmEntity::getAlarmHandledTime, System.currentTimeMillis() / 1000));
        if (rows > 0) {
            log.info("心跳恢复，自动关闭离线告警 {} 条 deviceNo={}", rows, deviceNo);
        }
        return rows;
    }

    @Override
    public int closeUnhandledAlarm(String deviceNo, AlarmType type) {
        //批次4：产生方恢复后关闭自己的未处理告警（谁开谁关）。不做门控：调用频率低（30s/1min
        //扫描轮），UPDATE 带 handled=0 条件空写无害（索引扫描 0 行）
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<DeviceAlarmEntity>()
                .eq(DeviceAlarmEntity::getDeviceNo, deviceNo)
                .eq(DeviceAlarmEntity::getAlarmType, type.getCode())
                .eq(DeviceAlarmEntity::getAlarmHandled, 0)
                .set(DeviceAlarmEntity::getAlarmHandled, 1)
                .set(DeviceAlarmEntity::getAlarmHandledTime, System.currentTimeMillis() / 1000));
        if (rows > 0) {
            log.info("告警自动关闭 {} 条 deviceNo={} type={}（恢复路径）", rows, deviceNo, type);
        }
        return rows;
    }

    @Override
    public long countUnhandled() {
        //批次4 指标：未处理告警数（处置面观测）
        return this.count(new LambdaQueryWrapper<DeviceAlarmEntity>()
                .eq(DeviceAlarmEntity::getAlarmHandled, 0));
    }

    @Override
    public PageUtils queryPage(DeviceAlarmForm form) {
        //T15：分页参数统一钳制（非法输入不再 500、超大 limit 不放行）
        int pageNum = PageParams.page(form.getPage());
        int limit = PageParams.limit(form.getLimit());

        IPage<DeviceAlarmEntity> page = this.page(
                new Page<>(pageNum, limit),
                new LambdaQueryWrapper<DeviceAlarmEntity>()
                        .eq(StringUtils.isNotBlank(form.getDeviceNo()), DeviceAlarmEntity::getDeviceNo, form.getDeviceNo())
                        .eq(form.getAlarmType() != null, DeviceAlarmEntity::getAlarmType, form.getAlarmType())
                        //0.4：缺省只看未确认（alarm_handled=0）——应急通道不淹没在历史里；看历史需显式传 1
                        .eq(DeviceAlarmEntity::getAlarmHandled,
                                form.getAlarmHandled() != null ? form.getAlarmHandled() : 0)
                        .orderByDesc(DeviceAlarmEntity::getAlarmCreatetime)
        );
        return new PageUtils(page);
    }
}
