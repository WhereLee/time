package com.reason.modules.sys.service;

import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.entity.SysUserTokenEntity;

import java.util.Set;

/**
 * 认证服务（原 ShiroService 改名，认证校验逻辑自 OAuth2Realm 迁入）
 */
public interface AuthService {

    /**
     * 校验 token 并返回已填充 roleType 的用户
     * 校验项：token 有效性/过期、账号状态、口令强制变更（白名单接口除外）
     *
     * @param token 请求携带的 token
     * @param uri   当前请求 URI（强改密码检查的豁免判断用）
     * @throws com.reason.common.exception.RRException 校验失败（token 失效/账号锁定/需先变更密码）
     */
    SysUserEntity verifyTokenAndGetUser(String token, String uri);

    /**
     * 获取用户权限列表
     */
    Set<String> queryUserPermissions(SysUserEntity user);

    /**
     * 根据 token 查询 token 实体
     */
    SysUserTokenEntity getUserToken(String token);

    /**
     * 根据用户 ID 查询用户
     */
    SysUserEntity getUser(Long userId);

    /**
     * 根据用户 ID 获取用户最大角色 1-开发员 2-系统管理员 其他-其他管理员 null-无角色
     */
    Long getRoleTypeByUserId(Long userId);
}
