package com.reason.modules.device.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 手动保持期注解（自定义注解 + AOP：治"手动 vs 自动打架"边界）
 *
 * <p>贴在管理端手动指令接口上：人一点按钮，说明人在现场接管这台设备——
 * ManualHoldAspect 随即写 Redis key（barrier:manual-hold:{deviceNo}，TTL=配置窗口），
 * 窗口期内 barrierAutoTask 自动规则对该设备让位（不下发校正指令）。</p>
 *
 * <p>为什么用 Redis 而不是库表：保持期是"自动到期的临时状态"——TTL 天生就是它的语义，
 * 库表实现需要额外的清理任务；且判定在自动任务热路径上，EXISTS 一次即答。</p>
 *
 * <p>与 @RepeatGuard 同贴一个方法时顺序无关紧要：防重拦截重复点击，
 * 保持期刷新窗口（同一人连续操作本就该持续让位自动规则）。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ManualHold {
}
