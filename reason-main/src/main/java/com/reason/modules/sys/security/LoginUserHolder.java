package com.reason.modules.sys.security;

import com.reason.modules.sys.entity.SysUserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户持有工具（替代原 Shiro SecurityUtils.getSubject().getPrincipal()）
 */
public class LoginUserHolder {

    private LoginUserHolder() {
    }

    /**
     * 获取当前登录用户；未认证上下文（白名单接口/匿名请求）返回 null
     */
    public static SysUserEntity getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SysUserEntity user) {
            return user;
        }
        return null;
    }
}
