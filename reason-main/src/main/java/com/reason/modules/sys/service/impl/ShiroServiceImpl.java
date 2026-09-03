/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.StringUtils;
import com.reason.modules.sys.dao.SysMenuDao;
import com.reason.modules.sys.dao.SysRoleDao;
import com.reason.modules.sys.dao.SysUserDao;
import com.reason.modules.sys.dao.SysUserTokenDao;
import com.reason.modules.sys.entity.SysMenuEntity;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.entity.SysUserTokenEntity;
import com.reason.modules.sys.service.ShiroService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class ShiroServiceImpl implements ShiroService {
    @Autowired
    private SysMenuDao sysMenuDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private SysUserTokenDao sysUserTokenDao;
    @Autowired
    private SysRoleDao sysRoleDao;

    /**
     * 获取用户权限列表
     * @param user
     * @return
     */
    @Override
    public Set<String> queryUserPermissions(SysUserEntity user) {
        List<String> permsList;

        //开发员，拥有最高权限
        if(user.developer()){
            List<SysMenuEntity> menuList = sysMenuDao.selectByMap(new MapUtils().put("menu_status",0));
            permsList = new ArrayList<>(menuList.size());
            for(SysMenuEntity menu : menuList){
                permsList.add(menu.getMenuPerms());
            }
        }else{
            permsList = sysMenuDao.queryPermsByUserId(user.getUserId());
        }
        //用户权限列表
        Set<String> permsSet = new HashSet<>();
        for(String perms : permsList){
            if(StringUtils.isBlank(perms)){
                continue;
            }
            permsSet.addAll(Arrays.asList(perms.trim().split(",")));
        }
        return permsSet;
    }

    /**
     * 根据token 查询用户信息
     * @param token
     * @return
     */
    @Override
    public SysUserTokenEntity getUserToken(String token) {
        return sysUserTokenDao.selectOne(
                new QueryWrapper<SysUserTokenEntity>()
                        .eq("token", token));
    }

    /**
     * 根据用户ID，查询用户
     * @param userId
     */
    @Override
    public SysUserEntity getUser(Long userId) {
        return sysUserDao.selectById(userId);
    }

    /**
     * 根据用户ID获取用户最大角色
     * @param userId
     * @return 1-开发员角色 2-系统管理员角色 其他-其他管理员角色 null-没有角色
     */
    @Override
    public Long getRoleTypeByUserId(Long userId) {
        return sysRoleDao.getRoleTypeByUserId(userId);
    }
}
