package com.reason.modules.device.aspect;

import com.reason.modules.device.annotation.ManualHold;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.form.DeviceCommandForm;
import com.reason.modules.sys.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 手动保持期切面（与 @ManualHold 配套的"执行者"）
 *
 * <p>注解声明"这是手动通道"，切面执行"开保持期"：proceed 前写 Redis key
 * barrier:manual-hold:{deviceNo}（value=操作人，TTL=配置窗口）——无论指令最终
 * 成败，人已在操作这台设备，自动规则都该让位（保持期语义针对"人接管"，不针对"指令结果"）。</p>
 */
@Slf4j
@Aspect
@Component
public class ManualHoldAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final BarrierProperties barrierProperties;

    public ManualHoldAspect(StringRedisTemplate stringRedisTemplate,
                            BarrierProperties barrierProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.barrierProperties = barrierProperties;
    }

    @Around("@annotation(com.reason.modules.device.annotation.ManualHold)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String deviceNo = extractDeviceNo(pjp.getArgs());
        if (deviceNo != null) {
            //防御：@ManualHold 只贴管理端接口（必有登录态），但切面自身不能成为故障点——
            //无登录上下文时跳过保持期写入（不阻断业务），记日志暴露异常调用路径
            var loginUser = LoginUserHolder.getLoginUser();
            if (loginUser == null) {
                log.warn("手动保持期跳过：无登录上下文 deviceNo={} method={}",
                        deviceNo, pjp.getSignature().getName());
            } else {
                Long userId = loginUser.getUserId();
                stringRedisTemplate.opsForValue().set(BarrierRedisKeys.MANUAL_HOLD_PREFIX + deviceNo,
                        String.valueOf(userId),
                        barrierProperties.getManualHoldMinutes(), TimeUnit.MINUTES);
                log.info("手动保持期开启 deviceNo={} userId={} 窗口={}min（期间自动规则让位）",
                        deviceNo, userId, barrierProperties.getManualHoldMinutes());
            }
        }
        return pjp.proceed();
    }

    /**
     * 从方法入参提取设备号（约定：手动指令接口第一个参数是 DeviceCommandForm）
     */
    private String extractDeviceNo(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof DeviceCommandForm form) {
                return form.getDeviceNo();
            }
        }
        return null;
    }
}
