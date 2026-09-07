package com.reason.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交/防连点注解（自定义注解 + AOP 三步曲的完整示例）
 *
 * <p>贴在管理端"操作型"接口上：同一操作者在防重窗口内重复调用（如连续点击两次
 * "升起"），由 RepeatGuardAspect 拦截直接拒绝——防止误操作把重复指令打到设备。
 *
 * <p>机制：Redis SETNX 原子占位（NX + EX 过期），窗口期内再次进入即判定重复。
 * 生产可扩展到分布式锁/幂等键，此处为横切关注点抽离的演示。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatGuard {

    /**
     * 防重窗口（毫秒），默认 3000：窗口内同一操作者重复调用被拒
     */
    long value() default 3000;
}
