package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

        DeviceAlarmEntity alarm = new DeviceAlarmEntity();
        alarm.setDeviceNo(deviceNo);
        alarm.setAlarmType(type.getCode());
        alarm.setAlarmContent(content);
        alarm.setAlarmHandled(0);
        alarm.setAlarmCreatetime(System.currentTimeMillis() / 1000);
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
    public PageUtils queryPage(DeviceAlarmForm form) {
        int pageNum = form.getPage() == null ? 1 : Integer.parseInt(form.getPage());
        int limit = form.getLimit() == null ? 10 : Integer.parseInt(form.getLimit());

        IPage<DeviceAlarmEntity> page = this.page(
                new Page<>(pageNum, limit),
                new LambdaQueryWrapper<DeviceAlarmEntity>()
                        .eq(StringUtils.isNotBlank(form.getDeviceNo()), DeviceAlarmEntity::getDeviceNo, form.getDeviceNo())
                        .eq(form.getAlarmType() != null, DeviceAlarmEntity::getAlarmType, form.getAlarmType())
                        .eq(form.getAlarmHandled() != null, DeviceAlarmEntity::getAlarmHandled, form.getAlarmHandled())
                        .orderByDesc(DeviceAlarmEntity::getAlarmCreatetime)
        );
        return new PageUtils(page);
    }
}
