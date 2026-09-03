/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service;

import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.entity.SysUserTokenEntity;

import java.util.Set;

/**
 * shiro相关接口
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface ShiroService {
    /**
     * 获取用户权限列表
     * @param user
     * @return
     */
    Set<String> queryUserPermissions(SysUserEntity user);

    /**
     * 根据token 查询用户信息
     * @param token
     * @return
     */
    SysUserTokenEntity getUserToken(String token);

    /**
     * 根据用户ID，查询用户
     * @param userId
     */
    SysUserEntity getUser(Long userId);

    /**
     * 根据用户ID获取用户最大角色
     * @param userId
     * @return 1-开发员角色 2-系统管理员角色 其他-其他管理员角色
     */
    Long getRoleTypeByUserId(Long userId);
}
