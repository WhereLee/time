package com.reason.common.aspect;

import com.reason.common.annotation.RepeatGuard;
import com.reason.common.exception.RRException;
import com.reason.modules.sys.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面（与 @RepeatGuard 配套的"执行者"）
 *
 * <p>自定义注解 + AOP 三步曲：注解只声明（@RepeatGuard = "这个接口要防连点"），
 * 切面负责执行（Around：proceed 前 Redis 原子占位，占不上 = 窗口内重复 -> 拒绝放行）。
 * Redis SETNX 保证多实例下同样原子（样例单实例，语义与生产一致）。</p>
 */
@Slf4j
@Aspect
@Order(1) //先于 @ManualHold(2)：防重拦截抛错时 ManualHold 切面不执行 -> 被拦点击不留保持期
@Component
public class RepeatGuardAspect {

    private static final String KEY_PREFIX = "rg:";

    private final StringRedisTemplate stringRedisTemplate;

    public RepeatGuardAspect(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(repeatGuard)")
    public Object around(ProceedingJoinPoint pjp, RepeatGuard repeatGuard) throws Throwable {
        //防重键 = 操作者 + 接口方法（同一人连点同一按钮才拦；不同人/不同操作互不影响）
        Long userId = LoginUserHolder.getLoginUser().getUserId();
        String key = KEY_PREFIX + userId + ":" + pjp.getSignature().getName();

        //SET NX EX：占位成功 = 窗口内首次；占位失败 = 窗口内重复，直接拒绝
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", repeatGuard.value(), TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(first)) {
            log.warn("防重复提交拦截 userId={} method={} 窗口={}ms",
                    userId, pjp.getSignature().getName(), repeatGuard.value());
            throw new RRException("操作过于频繁，请稍后再试");
        }
        return pjp.proceed();
    }
}
