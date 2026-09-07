package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
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
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 设备告警服务实现
 *
 * <p>去重用 Redis SETNX（barrier:alarm-dedup:{deviceNo}:{type}，TTL=配置窗口）：
 * 占位成功才落库——持续异常（如设备一直离线）在窗口内只喊一次，不刷屏；
 * 窗口过期后若异常仍在，再喊一次（"还在坏"的周期性提醒）。
 * 用 DB 查重（select count）会有查-插竞态且热路径多一次查询，SETNX 原子且 O(1)。</p>
 */
@Slf4j
@Service("deviceAlarmService")
public class DeviceAlarmServiceImpl extends ServiceImpl<DeviceAlarmDao, DeviceAlarmEntity>
        implements DeviceAlarmService {

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
        //告警是"喊给人听"的：warn 级日志进 error/warn 文件，运维侧可接日志告警渠道
        log.warn("设备告警 deviceNo={} type={} content={}", deviceNo, type.getDesc(), content);
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
    public PageUtils queryPage(DeviceAlarmForm form) {
        int pageNum = form.getPage() == null ? 1 : Integer.parseInt(form.getPage());
        int limit = form.getLimit() == null ? 10 : Integer.parseInt(form.getLimit());

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
