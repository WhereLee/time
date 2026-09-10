package com.reason.modules.device.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备代际重放守卫测试（批次8）：稳态直通不查历史、新代际放行、旧代际历史命中判重放、
 * Redis 异常 fail-open、register 幂等登记与 TTL
 */
@DisplayName("设备代际重放守卫(批次8)")
@ExtendWith(MockitoExtension.class)
class DeviceBootGenerationGuardTest {

    private static final String DEVICE_NO = "BARRIER-E-01";
    private static final String CURRENT_KEY = BarrierRedisKeys.BOOT_CURRENT_PREFIX + DEVICE_NO;
    private static final String HISTORY_KEY = BarrierRedisKeys.BOOT_HISTORY_PREFIX + DEVICE_NO;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @InjectMocks
    private DeviceBootGenerationGuard guard;

    @Test
    @DisplayName("同代际（稳态）：直通，不查历史集合")
    void 同代际_直通() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CURRENT_KEY)).thenReturn("boot-1");

        assertThat(guard.isReplay(DEVICE_NO, "boot-1")).isFalse();
        verify(stringRedisTemplate, never()).opsForSet();
    }

    @Test
    @DisplayName("首次上报（无当前代际）：直通")
    void 无当前代际_直通() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CURRENT_KEY)).thenReturn(null);

        assertThat(guard.isReplay(DEVICE_NO, "boot-1")).isFalse();
    }

    @Test
    @DisplayName("换代际且历史未见：放行（真重启新代际）")
    void 新代际未见_放行() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CURRENT_KEY)).thenReturn("boot-2");
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(HISTORY_KEY, "boot-3")).thenReturn(false);

        assertThat(guard.isReplay(DEVICE_NO, "boot-3")).isFalse();
    }

    @Test
    @DisplayName("旧代际历史命中：判定重放（调用方拒绝）")
    void 旧代际历史命中_重放() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CURRENT_KEY)).thenReturn("boot-2");
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(HISTORY_KEY, "boot-1")).thenReturn(true);

        assertThat(guard.isReplay(DEVICE_NO, "boot-1")).isTrue();
    }

    @Test
    @DisplayName("Redis 异常：fail-open 放行（不阻断设备上报生命线）")
    void Redis异常_放行() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CURRENT_KEY)).thenThrow(new RuntimeException("redis down"));

        assertThat(guard.isReplay(DEVICE_NO, "boot-9")).isFalse();
    }

    @Test
    @DisplayName("register 首写：SET 当前代际 + SADD 历史 + 设 TTL（30 天）")
    void register首次_写当前与历史并设TTL() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(HISTORY_KEY, "boot-1")).thenReturn(1L);

        guard.register(DEVICE_NO, "boot-1");

        verify(valueOperations).set(CURRENT_KEY, "boot-1");
        verify(stringRedisTemplate).expire(HISTORY_KEY, 30L, TimeUnit.DAYS);
    }

    @Test
    @DisplayName("register 幂等：历史已有（SADD 返回 0）不重置 TTL")
    void register重复_不重设TTL() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(HISTORY_KEY, "boot-1")).thenReturn(0L);

        guard.register(DEVICE_NO, "boot-1");

        verify(valueOperations).set(CURRENT_KEY, "boot-1");
        verify(stringRedisTemplate, never()).expire(anyString(), anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class));
    }

    @Test
    @DisplayName("register Redis 异常：不抛（加固层不反噬事件主链路）")
    void register异常_不抛() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis down")).when(valueOperations).set(CURRENT_KEY, "boot-1");

        assertThatCode(() -> guard.register(DEVICE_NO, "boot-1")).doesNotThrowAnyException();
    }
}
