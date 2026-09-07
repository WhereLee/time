package com.reason.modules.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceManualHoldDao;
import com.reason.modules.device.entity.DeviceManualHoldEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 手动保持期服务（0.2 语义修正 + 0.8 Redis 易失态修复的收口）
 *
 * <p>保持期 = "人在接管这台设备"的临时事实，语义针对人接管而非指令结果：
 * <ul>
 *   <li><b>受理后写入</b>（0.2）：只有手动指令真正受理成功才开保持期——失败/被拒/被防重拦截的
 *       点击不留让位窗（旧版 proceed 前写 key：失败点击也压制自动规则 30min，低权账号可续期压制）；</li>
 *   <li><b>Redis + DB 双写</b>（0.8）：Redis key 是热路径判定（TTL 自动到期），DB 是重建源——
 *       Redis 丢失后首个仲裁周期自动从 DB 惰性重建，人在杆下不被抢控（物理安全语义不依赖 Redis）。</li>
 * </ul></p>
 */
@Slf4j
@Service("manualHoldService")
public class ManualHoldService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DeviceManualHoldDao holdDao;
    private final BarrierProperties barrierProperties;

    public ManualHoldService(StringRedisTemplate stringRedisTemplate,
                             DeviceManualHoldDao holdDao,
                             BarrierProperties barrierProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.holdDao = holdDao;
        this.barrierProperties = barrierProperties;
    }

    /**
     * 开启保持期（受理成功后调用）：Redis key（TTL）+ DB 双写（新保持期覆盖旧的）
     */
    public void hold(String deviceNo, Long userId) {
        long now = System.currentTimeMillis() / 1000;
        long expire = now + barrierProperties.getManualHoldMinutes() * 60L;
        //Redis 热路径（TTL 自动到期）
        stringRedisTemplate.opsForValue().set(BarrierRedisKeys.MANUAL_HOLD_PREFIX + deviceNo,
                String.valueOf(userId), barrierProperties.getManualHoldMinutes(), TimeUnit.MINUTES);
        //DB 重建源（覆盖写：同设备新保持期替换旧的——先删后插防 PK 冲突）——落库失败不阻断（Redis 为主，尽力双写）
        try {
            holdDao.delete(new LambdaQueryWrapper<DeviceManualHoldEntity>()
                    .eq(DeviceManualHoldEntity::getDeviceNo, deviceNo));
            DeviceManualHoldEntity entity = new DeviceManualHoldEntity();
            entity.setDeviceNo(deviceNo);
            entity.setUserId(userId);
            entity.setExpireTime(expire);
            entity.setCreateTime(now);
            holdDao.insert(entity);
        } catch (RuntimeException e) {
            log.warn("保持期落库失败(Redis 仍生效,DB 重建源缺失) deviceNo={} cause={}", deviceNo, e.getMessage());
        }
        log.info("手动保持期开启 deviceNo={} userId={} 窗口={}min（期间自动规则让位）",
                deviceNo, userId, barrierProperties.getManualHoldMinutes());
    }

    /**
     * 保持期是否有效（autoTask/monitorTask 仲裁共用）：Redis 优先，miss 时 DB 兜底惰性重建——
     * Redis 丢失（FLUSHDB/重启）后首个仲裁周期自动恢复，无需启动钩子
     */
    public boolean isActive(String deviceNo) {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(BarrierRedisKeys.MANUAL_HOLD_PREFIX + deviceNo))) {
            return true;
        }
        //DB 兜底：未过期保持期 -> 重建 Redis key 并生效
        long now = System.currentTimeMillis() / 1000;
        DeviceManualHoldEntity hold = holdDao.selectOne(new LambdaQueryWrapper<DeviceManualHoldEntity>()
                .eq(DeviceManualHoldEntity::getDeviceNo, deviceNo)
                .gt(DeviceManualHoldEntity::getExpireTime, now));
        if (hold == null) {
            return false;
        }
        long remain = hold.getExpireTime() - now;
        stringRedisTemplate.opsForValue().set(BarrierRedisKeys.MANUAL_HOLD_PREFIX + deviceNo,
                String.valueOf(hold.getUserId()), remain, TimeUnit.SECONDS);
        log.info("保持期从 DB 惰性重建 deviceNo={} userId={} 剩余={}s（Redis 丢失恢复）",
                deviceNo, hold.getUserId(), remain);
        return true;
    }
}
