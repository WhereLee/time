package com.reason.modules.sys.security;

import com.reason.common.exception.RRException;
import com.reason.common.utils.ParamUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败防护单测（T19：账号×IP 组合锁 + IP 维度闸）
 */
@DisplayName("登录失败防护（T19）")
@ExtendWith(MockitoExtension.class)
class LoginAttemptGuardTest {

    private static final String LOGINNAME = "admin";
    private static final String IP = "127.0.0.1";
    private static final String ACCT_FAIL_KEY = "login:fail:acct:" + LOGINNAME + ":" + IP;
    private static final String ACCT_LOCK_KEY = "login:lock:acct:" + LOGINNAME + ":" + IP;
    private static final String IP_FAIL_KEY = "login:fail:ip:" + IP;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ParamUtils paramUtils;

    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LoginAttemptGuard(stringRedisTemplate, paramUtils);
        ReflectionTestUtils.setField(guard, "ipMax", 20);
        ReflectionTestUtils.setField(guard, "ipWindowSeconds", 600);
        // guard 经 stringRedisTemplate.opsForValue() 取操作对象；部分用例（如 onSuccess）不经此路径，lenient 兼容
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void policy(int attemptLimit, int lockTime) {
        when(paramUtils.getAttemptLimtAndLockTime())
                .thenReturn(Map.of("attemptLimit", attemptLimit, "lockTime", lockTime));
    }

    @Test
    @DisplayName("失败未达阈值：NORMAL，计数首增挂 TTL")
    void 失败未达阈值_NORMAL() {
        ReflectionTestUtils.setField(guard, "ipMax", 0);
        policy(5, 5);
        when(valueOperations.increment(ACCT_FAIL_KEY)).thenReturn(1L);

        assertThat(guard.onFailure(LOGINNAME, IP)).isEqualTo(LoginAttemptGuard.FailureOutcome.NORMAL);
        verify(stringRedisTemplate).expire(eq(ACCT_FAIL_KEY), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("账号×IP 达阈值：LOCKED_BY_ACCOUNT（上锁键 TTL=lockTime 分钟 + 重置计数）")
    void 达阈值_账号组合锁定() {
        ReflectionTestUtils.setField(guard, "ipMax", 0);
        policy(5, 5);
        when(valueOperations.increment(ACCT_FAIL_KEY)).thenReturn(5L);

        assertThat(guard.onFailure(LOGINNAME, IP)).isEqualTo(LoginAttemptGuard.FailureOutcome.LOCKED_BY_ACCOUNT);
        verify(stringRedisTemplate).delete(ACCT_FAIL_KEY);
        verify(valueOperations).set(ACCT_LOCK_KEY, "1", 300L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("预检命中组合锁键：抛锁定文案")
    void 预检_组合锁命中_抛() {
        policy(5, 5);
        when(stringRedisTemplate.hasKey(ACCT_LOCK_KEY)).thenReturn(true);

        assertThatThrownBy(() -> guard.assertNotBlocked(LOGINNAME, IP))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("账号已限时锁定");
    }

    @Test
    @DisplayName("IP 维度达上限：LOCKED_BY_IP（账号维度关闭时独立生效）")
    void IP维度达上限_锁定() {
        ReflectionTestUtils.setField(guard, "ipMax", 3);
        policy(0, 5);
        when(valueOperations.increment(IP_FAIL_KEY)).thenReturn(3L);

        assertThat(guard.onFailure(LOGINNAME, IP)).isEqualTo(LoginAttemptGuard.FailureOutcome.LOCKED_BY_IP);
    }

    @Test
    @DisplayName("预检命中 IP 闸：抛网络限速文案")
    void 预检_IP闸命中_抛() {
        policy(5, 5);
        when(stringRedisTemplate.hasKey(ACCT_LOCK_KEY)).thenReturn(false);
        when(valueOperations.get(IP_FAIL_KEY)).thenReturn("20");

        assertThatThrownBy(() -> guard.assertNotBlocked(LOGINNAME, IP))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("登录失败次数过多");
    }

    @Test
    @DisplayName("双维度同时开启：每次失败两维度都记录（防轮换账号名绕过）")
    void 双维度都记录() {
        policy(5, 5);
        when(valueOperations.increment(IP_FAIL_KEY)).thenReturn(1L);
        when(valueOperations.increment(ACCT_FAIL_KEY)).thenReturn(1L);

        assertThat(guard.onFailure(LOGINNAME, IP)).isEqualTo(LoginAttemptGuard.FailureOutcome.NORMAL);
        verify(valueOperations).increment(IP_FAIL_KEY);
        verify(valueOperations).increment(ACCT_FAIL_KEY);
    }

    @Test
    @DisplayName("登录成功：清理三键（幂等）")
    void 成功后清理三键() {
        guard.onSuccess(LOGINNAME, IP);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(stringRedisTemplate).delete(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(ACCT_FAIL_KEY, ACCT_LOCK_KEY, IP_FAIL_KEY);
    }

    @Test
    @DisplayName("loginname 超长防御：键经截断规范化（不产生超长 key）")
    void 超长loginname_截断() {
        ReflectionTestUtils.setField(guard, "ipMax", 0);
        policy(5, 5);
        String longName = "x".repeat(200);
        String truncated = "x".repeat(64);
        when(valueOperations.increment("login:fail:acct:" + truncated + ":" + IP)).thenReturn(1L);

        assertThat(guard.onFailure(longName, IP)).isEqualTo(LoginAttemptGuard.FailureOutcome.NORMAL);
        verify(valueOperations).increment("login:fail:acct:" + truncated + ":" + IP);
    }
}
