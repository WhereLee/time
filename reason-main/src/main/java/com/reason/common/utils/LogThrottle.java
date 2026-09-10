package com.reason.common.utils;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 重复告警日志节流器（批次8 P3——治 B4 实测的拒绝路径日志放大）
 *
 * <p>背景：限流/验签拒绝路径每请求一条 warn，洪峰下实测 ≈100 行/秒、1.3MB/分钟（见
 * document/knowledge/heartbeat-event-capacity-profile.md）。限流保护了业务与 MQ，
 * 但日志侧没有配额——攻击速率压在闸内即可持续放大磁盘/IO。</p>
 *
 * <p>策略：per-key 时间窗（默认 5s）最多输出一条 warn；窗口内被抑制的条数累计，
 * 下一次放行时附在消息尾部（"窗口内另抑制同类日志 N 条"）——可见性不丢（首条+计数），
 * 输出量从 O(请求) 降为 O(窗口×键)。</p>
 *
 * <p>内存边界：键数上限（默认 1024），超出后新键归并到溢出键共享一个窗口——攻击者用
 * 随机 deviceNo 刷日志时，输出量仍有上界，且计数器不会无界增长。并发下上限近似成立
 * （size 检查非原子，最坏略超）。</p>
 *
 * <p>定位：日志侧"软配额"，不改变业务语义也不吞返回码；调用方只在"确定要输出"的分支调用，
 * 被抑制时不做任何字符串构造（message 用 Supplier 惰性求值）。</p>
 */
@Component
public class LogThrottle {

    /** 默认窗口：5s（B4 洪水 ~100 行/秒 → 每键每窗口一条，量级压到可人工消化的程度） */
    private static final long DEFAULT_WINDOW_MILLIS = 5000L;

    /** 默认键数上限（防随机键膨胀；超出归并溢出键） */
    private static final int DEFAULT_MAX_KEYS = 1024;

    /** 溢出键：键数超限后的公共窗口 */
    private static final String OVERFLOW_KEY = "__overflow__";

    private final long windowMillis;
    private final int maxKeys;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public LogThrottle() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_MAX_KEYS, System::currentTimeMillis);
    }

    /**
     * 测试接缝：可注入窗口/上限/时钟（时钟假造使窗口行为可确定性断言）
     */
    LogThrottle(long windowMillis, int maxKeys, LongSupplier clock) {
        this.windowMillis = windowMillis;
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    /**
     * 是否放行本次输出（窗口内首条=true；同键窗口内后续=false 并计入抑制数）
     */
    public boolean allow(String key) {
        Counter counter = counters.computeIfAbsent(effectiveKey(key), k -> new Counter());
        long now = clock.getAsLong();
        while (true) {
            long last = counter.lastLogAt.get();
            if (now - last < windowMillis) {
                counter.suppressed.incrementAndGet();
                return false;
            }
            // CAS 竞争：多线程同窗口并发时仅一个赢家放行，其余走抑制分支
            if (counter.lastLogAt.compareAndSet(last, now)) {
                return true;
            }
        }
    }

    /**
     * 取走并清零该键自上次放行以来被抑制的条数（调用方仅在 allow=true 后取用）
     */
    public long drainSuppressed(String key) {
        Counter counter = counters.get(effectiveKey(key));
        return counter == null ? 0L : counter.suppressed.getAndSet(0L);
    }

    /**
     * 节流 warn：窗口内首条输出原文；携带抑制计数时在尾部附计数。
     * message 用 Supplier 惰性求值——被抑制时零字符串构造（热路径友好）
     */
    public void warn(Logger log, String key, Supplier<String> message) {
        if (!allow(key)) {
            return;
        }
        long suppressed = drainSuppressed(key);
        String text = message.get();
        if (suppressed > 0) {
            log.warn(text + "（窗口内另抑制同类日志 " + suppressed + " 条）");
        } else {
            log.warn(text);
        }
    }

    /**
     * 键数上限兜底：表未满或键已在表中 → 原键；超出 → 溢出键（共享窗口，输出量仍有上界）
     */
    private String effectiveKey(String key) {
        if (counters.size() < maxKeys || counters.containsKey(key)) {
            return key;
        }
        return OVERFLOW_KEY;
    }

    /**
     * 单键窗口状态：lastLogAt=上次放行墙钟；suppressed=窗口内被抑制累计
     */
    private static final class Counter {
        private final AtomicLong lastLogAt = new AtomicLong(0L);
        private final AtomicLong suppressed = new AtomicLong(0L);
    }
}
