package com.reason.modules.device.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 上行入口限流（批次5 横切治理）：Redis 令牌桶双层
 *
 * <p>目的与分工（红队清单"上行入口限流：per-device RPS + 全局水位闸"）：</p>
 * <ol>
 *   <li>per-device 桶：约束单设备洪峰（心跳约 0.1rps/台正常，桶 5rps/burst10——
 *       只有"发疯设备"才会触顶），心跳/事件两类都查；</li>
 *   <li>全局水位闸：平台自我保护——全局上行超限时拒绝"非关键上报"，定义=事件上报；
 *       心跳是判活生命线（被误拒会级联误判离线→错误自动指令），<b>不</b>受全局闸约束。</li>
 * </ol>
 *
 * <p>位置：两个设备入口（心跳/事件）在 HMAC 鉴权<b>之前</b>调用——先限流再验签，
 * 防未认证洪峰把 HMAC 计算（每跳一次 DB 查密钥）打满。</p>
 *
 * <p>降级策略（fail-open）：Redis 异常时放行 + WARN——限流组件故障绝不能把全部设备
 * 上行拒之门外（可用性优先；重复流量由下游判重/幂等吸收）。</p>
 */
@Slf4j
@Component
public class DeviceTrafficGuard {

    /**
     * 令牌桶 LUA（HMGET tokens/ts → 按时间差补水 → 不足拒绝 → 扣减回写）：
     * 多命令必须原子，LUA 单请求往返；返回 1=放行 0=拒绝
     */
    private static final RedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>(
            "local key = KEYS[1] "
                    + "local rate = tonumber(ARGV[1]) "
                    + "local burst = tonumber(ARGV[2]) "
                    + "local now = tonumber(ARGV[3]) "
                    + "local data = redis.call('HMGET', key, 'tokens', 'ts') "
                    + "local tokens = tonumber(data[1]) "
                    + "local ts = tonumber(data[2]) "
                    + "if tokens == nil then tokens = burst; ts = now end "
                    + "local delta = math.max(0, now - ts) / 1000 "
                    + "tokens = math.min(burst, tokens + delta * rate) "
                    + "local allowed = 0 "
                    + "if tokens >= 1 then tokens = tokens - 1; allowed = 1 end "
                    + "redis.call('HSET', key, 'tokens', tokens, 'ts', now) "
                    + "local ttl = math.max(1000, math.ceil(burst / rate * 1000) * 2) "
                    + "redis.call('PEXPIRE', key, ttl) "
                    + "return allowed",
            Long.class);

    /** per-device 桶键前缀（barrier 前缀与升降杆域 Redis 键族一致） */
    private static final String DEVICE_BUCKET_PREFIX = "barrier:traffic:dev:";

    /** 全局水位桶键 */
    private static final String GLOBAL_BUCKET_KEY = "barrier:traffic:global";

    /** deviceNo 进 key 前长度上限（防超长 key） */
    private static final int MAX_DEVICE_NO_LEN = 64;

    private final StringRedisTemplate stringRedisTemplate;
    private final DeviceTrafficProperties properties;

    public DeviceTrafficGuard(StringRedisTemplate stringRedisTemplate, DeviceTrafficProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    /**
     * 心跳上行准入（关键通道：仅 per-device 桶，不受全局水位闸——判活生命线）
     */
    public void assertHeartbeatAllowed(String deviceNo) {
        if (!properties.isEnabled()) {
            return;
        }
        checkDeviceBucket(deviceNo, "HEARTBEAT");
    }

    /**
     * 事件上行准入（非关键：per-device 桶 + 全局水位闸——超限拒绝事件，保护平台）
     */
    public void assertEventAllowed(String deviceNo) {
        if (!properties.isEnabled()) {
            return;
        }
        checkDeviceBucket(deviceNo, "EVENT");
        if (!tryAcquire(GLOBAL_BUCKET_KEY, properties.getGlobalRps(), properties.getGlobalBurst())) {
            log.warn("[上行限流] 全局水位超限，拒绝事件上报 deviceNo={}", deviceNo);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "上行水位超限，请稍后重试");
        }
    }

    private void checkDeviceBucket(String deviceNo, String category) {
        if (!tryAcquire(DEVICE_BUCKET_PREFIX + normalize(deviceNo),
                properties.getDeviceRps(), properties.getDeviceBurst())) {
            log.warn("[上行限流] 单设备超限拒绝 category={} deviceNo={}（{}rps 桶满）",
                    category, deviceNo, properties.getDeviceRps());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "设备上行频率超限");
        }
    }

    /**
     * 令牌桶取一个令牌：true=放行；false=拒绝；Redis 异常=放行（fail-open + WARN）
     */
    private boolean tryAcquire(String bucketKey, int rate, int burst) {
        try {
            Long allowed = stringRedisTemplate.execute(TOKEN_BUCKET_SCRIPT,
                    List.of(bucketKey),
                    String.valueOf(rate), String.valueOf(burst),
                    String.valueOf(System.currentTimeMillis()));
            return allowed == null || allowed == 1L;
        } catch (Exception e) {
            log.warn("[上行限流] Redis 异常降级放行（fail-open）bucket={} cause={}", bucketKey, e.getMessage());
            return true;
        }
    }

    private static String normalize(String deviceNo) {
        if (deviceNo == null || deviceNo.isBlank()) {
            // 未携带设备号（伪造/配置错位）的请求共用一个桶——限流先于鉴权，集中约束异常流量
            return "__unknown__";
        }
        String s = deviceNo.trim();
        return s.length() > MAX_DEVICE_NO_LEN ? s.substring(0, MAX_DEVICE_NO_LEN) : s;
    }
}
