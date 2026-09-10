package com.reason.modules.device.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 上行入口限流单测（批次5：per-device 桶 + 全局水位闸 + fail-open 降级）
 */
@DisplayName("上行入口限流（批次5）")
class DeviceTrafficGuardTest {

    private StringRedisTemplate stringRedisTemplate;
    private DeviceTrafficProperties properties;
    private DeviceTrafficGuard guard;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        properties = new DeviceTrafficProperties();
        guard = new DeviceTrafficGuard(stringRedisTemplate, properties);
    }

    private void stubScript(Object... returns) {
        var stubbing = when(stringRedisTemplate.execute(any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString()));
        if (returns.length == 1) {
            stubbing.thenReturn(returns[0]);
        } else {
            stubbing.thenReturn(returns[0], returns[1]);
        }
    }

    @Test
    @DisplayName("心跳：per-device 桶放行（只查一处，不受全局闸）")
    void 心跳_桶放行() {
        stubScript(1L);

        assertThatCode(() -> guard.assertHeartbeatAllowed("BARRIER-B-01")).doesNotThrowAnyException();
        verify(stringRedisTemplate, times(1)).execute(any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("心跳：per-device 桶拒绝 -> 429 设备上行频率超限")
    void 心跳_桶拒绝_429() {
        stubScript(0L);

        assertThatThrownBy(() -> guard.assertHeartbeatAllowed("BARRIER-B-01"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("设备上行频率超限");
    }

    @Test
    @DisplayName("事件：设备桶过、全局水位拒 -> 429（非关键上报被保护性拒绝）")
    void 事件_全局水位拒绝_429() {
        stubScript(1L, 0L);

        assertThatThrownBy(() -> guard.assertEventAllowed("BARRIER-B-01"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("上行水位超限");
        verify(stringRedisTemplate, times(2)).execute(any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("事件：双桶都放行 -> 无异常（设备桶+全局桶各查一次）")
    void 事件_双桶放行() {
        stubScript(1L);

        assertThatCode(() -> guard.assertEventAllowed("BARRIER-B-01")).doesNotThrowAnyException();
        verify(stringRedisTemplate, times(2)).execute(any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("开关关闭：直接放行且不触碰 Redis")
    void 开关关闭_不碰redis() {
        properties.setEnabled(false);

        assertThatCode(() -> {
            guard.assertHeartbeatAllowed("BARRIER-B-01");
            guard.assertEventAllowed("BARRIER-B-01");
        }).doesNotThrowAnyException();
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("Redis 异常：fail-open 放行（限流组件故障不阻断设备上行）")
    void redis异常_降级放行() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> {
            guard.assertHeartbeatAllowed("BARRIER-B-01");
            guard.assertEventAllowed("BARRIER-B-01");
        }).doesNotThrowAnyException();
    }
}
