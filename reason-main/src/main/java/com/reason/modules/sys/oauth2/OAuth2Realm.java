/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.oauth2;

import com.reason.common.exception.RRException;
import com.reason.common.utils.HttpContextUtils;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.service.ShiroService;
import com.reason.modules.sys.entity.SysUserTokenEntity;
import com.reason.modules.sys.service.SysUserService;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * 认证
 *
 * @author Mark sunlightcs@gmail.com
 */
@Component
public class OAuth2Realm extends AuthorizingRealm {
    @Autowired
    private ShiroService shiroService;
    @Autowired
    @Lazy
    private SysUserService sysUserService;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof OAuth2Token;
    }

    /**
     * 授权(验证权限时调用)
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SysUserEntity user = (SysUserEntity)principals.getPrimaryPrincipal();

        //用户权限列表
        Set<String> permsSet = shiroService.queryUserPermissions(user);

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setStringPermissions(permsSet);
        return info;
    }

    /**
     * 认证(登录时调用)
     * org.apache.shiro.web.filter.authc.AuthenticatingFilter类的 executeLogin()后调用
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String accessToken = (String) token.getPrincipal();

        //根据accessToken，查询用户信息
        SysUserTokenEntity tokenEntity = shiroService.getUserToken(accessToken);
        //token失效
        if(tokenEntity == null || tokenEntity.getExpiretime() < (System.currentTimeMillis()/1000)){
            throw new IncorrectCredentialsException("token失效，请重新登录");
        }

        //查询用户信息
        SysUserEntity user = shiroService.getUser(tokenEntity.getUserId());
        //账号锁定
        if(!user.open())
            throw new LockedAccountException("账号已被锁定,请联系管理员");

        //口令强制变更
        HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
        String url = request.getRequestURI();
        if (!"/api/sys/menu/nav".equals(url) && !"/api/sys/user/info".equals(url) && !"/api/sys/logout".equals(url)
                && !"/api/sys/user/password".equals(url)) {
            if (sysUserService.getChangeForce(user) == 3)
                throw new RRException("请先变更密码", 888);
        }

        //查询用户的最大权限 1-开发员角色 2-系统管理员角色 其他-其他管理员角色 null-没有角色
        Long roleType = shiroService.getRoleTypeByUserId(tokenEntity.getUserId());
        user.setRoleType(roleType);

        SimpleAuthenticationInfo info = new SimpleAuthenticationInfo(user, accessToken, getName());
        return info;
    }
}
