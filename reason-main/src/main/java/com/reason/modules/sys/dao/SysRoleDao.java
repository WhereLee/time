/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.sys.entity.SysRoleEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色管理
 *
 * @author Mark sunlightcs@gmail.com
 */
@Repository
public interface SysRoleDao extends BaseMapper<SysRoleEntity> {

	/**
	 * 查询用户所拥有的角色权限
	 * @param userId
	 * @return
	 */
	List<SysRoleEntity> queryRoleByUserId(Long userId);

	/**
	 * 查询用户拥有的角色ID列表
	 * @param userId
	 * @return
	 */
	List<Long> queryRoleIdByUserId(Long userId);

	/**
	 * 查询用户自己创建的角色ID列表
	 * @param userId
	 * @return
	 */
	List<Long> queryRoleIdByCreator(Long userId);

	/**
	 * 根据用户ID获取用户最大角色
	 * @param userId
	 * @return 1-开发员角色 2-系统管理员角色 其他-其他管理员角色
	 */
	Long getRoleTypeByUserId(Long userId);

	/**
	 * 根据用户ID获取角色名称 多个用逗号拼接
	 * @param userId
	 * @return
	 */
	String getRoleNamesByUserId(Long userId);
}
