package com.reason.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.reason.common.exception.RRException;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.StringUtils;
import com.reason.modules.sys.dao.SysMenuDao;
import com.reason.modules.sys.dao.SysRoleDao;
import com.reason.modules.sys.dao.SysUserDao;
import com.reason.modules.sys.dao.SysUserTokenDao;
import com.reason.modules.sys.entity.SysMenuEntity;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.entity.SysUserTokenEntity;
import com.reason.modules.sys.service.AuthService;
import com.reason.modules.sys.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    /** 强改密码检查的豁免接口（导航/用户信息/登出/改密码），与原 OAuth2Realm 保持一致 */
    private static final Set<String> CHANGE_PASSWORD_EXEMPT_URIS = Set.of(
            "/api/sys/menu/nav", "/api/sys/user/info", "/api/sys/logout", "/api/sys/user/password");

    @Autowired
    private SysMenuDao sysMenuDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private SysUserTokenDao sysUserTokenDao;
    @Autowired
    private SysRoleDao sysRoleDao;
    @Autowired
    @Lazy
    private SysUserService sysUserService;

    /**
     * 校验 token 并返回用户（原 OAuth2Realm#doGetAuthenticationInfo 逻辑迁入）
     */
    @Override
    public SysUserEntity verifyTokenAndGetUser(String token, String uri) {
        //根据 accessToken，查询用户信息
        SysUserTokenEntity tokenEntity = getUserToken(token);
        //token失效
        if (tokenEntity == null || tokenEntity.getExpiretime() < (System.currentTimeMillis() / 1000)) {
            throw new RRException("token失效，请重新登录");
        }

        //查询用户信息（token 有效但用户已被删除的防御，原 Shiro 版本此处存在 NPE 缺陷）
        SysUserEntity user = getUser(tokenEntity.getUserId());
        if (user == null) {
            throw new RRException("账号不存在，请重新登录");
        }
        //账号锁定
        if (!user.open()) {
            throw new RRException("账号已被锁定,请联系管理员");
        }

        //口令强制变更（豁免接口除外）
        if (!CHANGE_PASSWORD_EXEMPT_URIS.contains(uri)) {
            if (sysUserService.getChangeForce(user) == 3) {
                throw new RRException("请先变更密码", 888);
            }
        }

        //查询用户的最大权限 1-开发员角色 2-系统管理员角色 其他-其他管理员角色 null-没有角色
        Long roleType = getRoleTypeByUserId(tokenEntity.getUserId());
        user.setRoleType(roleType);
        return user;
    }

    /**
     * 获取用户权限列表
     */
    @Override
    public Set<String> queryUserPermissions(SysUserEntity user) {
        List<String> permsList;

        //开发员，拥有最高权限
        if (user.developer()) {
            List<SysMenuEntity> menuList = sysMenuDao.selectByMap(new MapUtils().put("menu_status", 0));
            permsList = new ArrayList<>(menuList.size());
            for (SysMenuEntity menu : menuList) {
                permsList.add(menu.getMenuPerms());
            }
        } else {
            permsList = sysMenuDao.queryPermsByUserId(user.getUserId());
        }
        //用户权限列表
        Set<String> permsSet = new HashSet<>();
        for (String perms : permsList) {
            if (StringUtils.isBlank(perms)) {
                continue;
            }
            permsSet.addAll(Arrays.asList(perms.trim().split(",")));
        }
        return permsSet;
    }

    /**
     * 根据 token 查询用户信息
     */
    @Override
    public SysUserTokenEntity getUserToken(String token) {
        return sysUserTokenDao.selectOne(
                new QueryWrapper<SysUserTokenEntity>()
                        .eq("token", token));
    }

    /**
     * 根据用户ID，查询用户
     */
    @Override
    public SysUserEntity getUser(Long userId) {
        return sysUserDao.selectById(userId);
    }

    /**
     * 根据用户ID获取用户最大角色
     */
    @Override
    public Long getRoleTypeByUserId(Long userId) {
        return sysRoleDao.getRoleTypeByUserId(userId);
    }
}
