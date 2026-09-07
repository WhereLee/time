package com.reason.modules.device.aspect;

import com.reason.modules.device.annotation.ManualHold;
import com.reason.modules.device.form.DeviceCommandForm;
import com.reason.modules.device.service.ManualHoldService;
import com.reason.modules.sys.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 手动保持期切面（与 @ManualHold 配套的"执行者"——0.2 语义修正）
 *
 * <p>注解声明"这是手动通道"，切面执行"开保持期"。<b>受理后写入</b>：proceed 成功
 * （指令真实受理）才开保持期——失败/被拒/被 @RepeatGuard 拦截的点击不留让位窗
 * （旧版 proceed 前写 key：失败点击也压制自动规则 30min，低权账号可持续制造被拒点击压制自动）。
 * 保持期双写 Redis+DB（Redis 丢失后由 DB 惰性重建，见 ManualHoldService）。</p>
 */
@Slf4j
@Aspect
@Order(2) //在 @RepeatGuard(1) 之后执行：防重拦截抛错时不进入本切面 -> 被拦点击不写保持期
@Component
public class ManualHoldAspect {

    private final ManualHoldService manualHoldService;

    public ManualHoldAspect(ManualHoldService manualHoldService) {
        this.manualHoldService = manualHoldService;
    }

    @Around("@annotation(com.reason.modules.device.annotation.ManualHold)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        //先执行业务：只有受理成功（不抛异常）才开保持期
        Object result = pjp.proceed();

        String deviceNo = extractDeviceNo(pjp.getArgs());
        if (deviceNo == null) {
            return result;
        }
        //防御：@ManualHold 只贴管理端接口（必有登录态），但切面自身不能成为故障点——
        //无登录上下文时跳过保持期写入（不阻断业务），记日志暴露异常调用路径
        var loginUser = LoginUserHolder.getLoginUser();
        if (loginUser == null) {
            log.warn("手动保持期跳过：无登录上下文 deviceNo={} method={}",
                    deviceNo, pjp.getSignature().getName());
        } else {
            manualHoldService.hold(deviceNo, loginUser.getUserId());
        }
        return result;
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
