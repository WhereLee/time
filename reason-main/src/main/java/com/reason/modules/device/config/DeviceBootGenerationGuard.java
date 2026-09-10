package com.reason.modules.device.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 设备代际重放守卫（批次8——补协议 v2 §3.3 序守卫的跨代际盲区）
 *
 * <p>序守卫只守"同 bootId 单调递增"，对 bootId 变化一律接受并重置基线；一份被截获的旧代际事件
 * 重放时 bootId 与当前不同，会被当"设备重启新代际"接受——台账状态回退、极端下误销在途流水。
 * 本守卫维持"已见代际集合"：进程启动 UUID 不复用，新代际必然历史未见，历史命中=重放。</p>
 *
 * <p>分工：DB 序守卫仍是权威（同代际乱序/重复）；本守卫只在"代际切换"这一稀有时刻介入——
 * 事件与当前代际一致时一次 GET 直通（稳态零额外代价），换代际时才查历史；Redis 异常 fail-open
 * （放行 + WARN——降级为批次8 之前的现状，不阻断设备上报这一生命线）。</p>
 *
 * <p>边界：Redis 数据丢失（FLUSH/回退）后历史清空，守卫失效（与 seq 回退同一事故面，
 * 由启动对齐/对账升级告警兜底，不在此重复防护）；多实例下两个新代际并发竞争窗口极小，
 * 退化行为=批次8 现状（接受），无正确性危害增量。</p>
 */
@Slf4j
@Component
public class DeviceBootGenerationGuard {

    /** 历史代际保留时长（重启代际只增不减，30 天足够覆盖"迟到重投/重放"的现实时间窗） */
    private static final long HISTORY_TTL_DAYS = 30;

    private final StringRedisTemplate stringRedisTemplate;

    public DeviceBootGenerationGuard(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 判定是否"已见代际的重放"：
     * <ul>
     *   <li>与当前代际一致（稳态）→ false（直通，不查历史）；</li>
     *   <li>换代际且历史命中 → true（重放，调用方拒绝）；</li>
     *   <li>换代际但历史未见 → false（真重启新代际，调用方接受后 register）；</li>
     *   <li>Redis 异常 → false（fail-open 降级 + WARN）。</li>
     * </ul>
     */
    public boolean isReplay(String deviceNo, String bootId) {
        try {
            String current = stringRedisTemplate.opsForValue().get(BarrierRedisKeys.BOOT_CURRENT_PREFIX + deviceNo);
            if (current == null || current.equals(bootId)) {
                return false;
            }
            return Boolean.TRUE.equals(
                    stringRedisTemplate.opsForSet().isMember(BarrierRedisKeys.BOOT_HISTORY_PREFIX + deviceNo, bootId));
        } catch (RuntimeException e) {
            log.warn("代际重放判定异常，降级放行 deviceNo={} bootId={} cause={}", deviceNo, bootId, e.getMessage());
            return false;
        }
    }

    /**
     * 事件被台账接受后登记代际（幂等）：current=bootId（快路径基线）+ 历史 SADD（TTL 仅首写设置）。
     * 登记失败仅 WARN：守卫是加固层不是主链路，台账已按 DB 序守卫正确推进
     */
    public void register(String deviceNo, String bootId) {
        try {
            stringRedisTemplate.opsForValue().set(BarrierRedisKeys.BOOT_CURRENT_PREFIX + deviceNo, bootId);
            Long added = stringRedisTemplate.opsForSet()
                    .add(BarrierRedisKeys.BOOT_HISTORY_PREFIX + deviceNo, bootId);
            if (added != null && added > 0) {
                stringRedisTemplate.expire(BarrierRedisKeys.BOOT_HISTORY_PREFIX + deviceNo,
                        HISTORY_TTL_DAYS, TimeUnit.DAYS);
            }
        } catch (RuntimeException e) {
            log.warn("代际登记失败（守卫降级为无历史，DB 序守卫仍权威）deviceNo={} bootId={} cause={}",
                    deviceNo, bootId, e.getMessage());
        }
    }
}
