package com.reason.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 重复告警日志节流器测试（批次8 P3）：窗口内首条放行+抑制计数、窗口过期恢复、
 * 惰性消息不构造、键数上限溢出兜底、并发 CAS 单放行
 */
@DisplayName("重复告警日志节流器(批次8 P3)")
class LogThrottleTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private LogThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new LogThrottle(5000L, 4, now::get);
    }

    @Test
    @DisplayName("窗口内：首条放行，重复被抑制并计数；drain 取走清零")
    void 窗口内_首条放行重复抑制() {
        assertThat(throttle.allow("k")).isTrue();
        assertThat(throttle.allow("k")).isFalse();
        assertThat(throttle.allow("k")).isFalse();

        assertThat(throttle.drainSuppressed("k")).isEqualTo(2L);
        assertThat(throttle.drainSuppressed("k")).isZero();
    }

    @Test
    @DisplayName("窗口过期：恢复放行，warn 尾部携带窗口内抑制计数")
    void 窗口过期_恢复放行并带计数() {
        throttle.allow("k");
        throttle.allow("k");
        throttle.allow("k");
        now.addAndGet(5000L);

        Logger log = mock(Logger.class);
        throttle.warn(log, "k", () -> "boom");

        verify(log).warn("boom（窗口内另抑制同类日志 2 条）");
    }

    @Test
    @DisplayName("首条 warn：原文输出，无计数后缀")
    void 首条warn_无计数() {
        Logger log = mock(Logger.class);
        throttle.warn(log, "k", () -> "boom");

        verify(log).warn("boom");
    }

    @Test
    @DisplayName("被抑制：不输出且不构造消息（Supplier 不执行——热路径零字符串开销）")
    void 被抑制_不输出不构造() {
        Logger log = mock(Logger.class);
        AtomicInteger constructions = new AtomicInteger();
        Runnable emit = () -> throttle.warn(log, "k", () -> {
            constructions.incrementAndGet();
            return "boom";
        });

        emit.run();
        emit.run();
        emit.run();

        verify(log).warn("boom");
        assertThat(constructions).hasValue(1);
    }

    @Test
    @DisplayName("不同键互不影响（各键独立窗口）")
    void 不同键_独立窗口() {
        assertThat(throttle.allow("a")).isTrue();
        assertThat(throttle.allow("b")).isTrue();
        assertThat(throttle.allow("a")).isFalse();
        assertThat(throttle.allow("b")).isFalse();
    }

    @Test
    @DisplayName("键数超上限：新键归并溢出键共享窗口（随机键攻击下输出量仍有上界）")
    void 键数超限_溢出键兜底() {
        throttle = new LogThrottle(5000L, 2, now::get);
        assertThat(throttle.allow("k1")).isTrue();
        assertThat(throttle.allow("k2")).isTrue();
        assertThat(throttle.allow("k3")).isTrue();  // 溢出窗口首条
        assertThat(throttle.allow("k4")).isFalse(); // 溢出窗口内被抑制
        assertThat(throttle.allow("k3")).isFalse(); // 同溢出键

        assertThat(throttle.drainSuppressed("k4")).isEqualTo(2L);
    }

    @Test
    @DisplayName("并发同窗口：仅一条放行（CAS 裁决，抑制计数无丢失）")
    void 并发同窗口_仅一条放行() {
        AtomicInteger allowed = new AtomicInteger();
        IntStream.range(0, 64).parallel().forEach(i -> {
            if (throttle.allow("hot")) {
                allowed.incrementAndGet();
            }
        });

        assertThat(allowed).hasValue(1);
        assertThat(throttle.drainSuppressed("hot")).isEqualTo(63L);
    }
}
