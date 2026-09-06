/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.sys.entity.SysRoleEntity;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.form.SysRoleForm;
import com.reason.modules.sys.vo.SysRoleVO;

import java.util.List;


/**
 * 角色
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysRoleService extends IService<SysRoleEntity> {

	/**
	 * 分页查询角色
	 * @param form
	 * @return
	 */
	PageUtils queryPage(SysRoleForm form);

	/**
	 * 不分页查询角色-下拉查询
	 * 登录用户所有的角色权限
	 * @param form
	 * @return
	 */
	List<SysRoleEntity> queryRole(SysRoleForm form);

	/**
	 * 根据ID 获取角色信息信息（包括菜单信息和区域信息）
	 * @param roleId
	 * @return
	 */
	SysRoleEntity getInfo(Long roleId);

	/**
	 * 保存角色 && 角色菜单权限 && 角色区域权限
	 * @param roleVO
	 * @param user 操作人
	 */
	void saveRole(SysRoleVO roleVO, SysUserEntity user);

	/**
	 * 修改角色（更新创建人） && 角色菜单权限 && 角色区域权限
	 * @param roleVO
	 * @param user 操作人
	 */
	void updateRole(SysRoleVO roleVO, SysUserEntity user);

	/**
	 * 修改角色基本信息
	 * @param roleVO
	 * @param user 操作人
	 */
	void updateRoleBase(SysRoleVO roleVO, SysUserEntity user);

	/**
	 * 修改角色菜单权限
	 * @param roleVO
	 * @param user
	 */
	void updateRoleMenu(SysRoleVO roleVO, SysUserEntity user);

	/**
	 * 删除角色（逻辑） && 角色菜单权限 && 角色区域权限
	 * @param roleId
	 * @param user 操作人
	 */
	void deleteRole(Long roleId, SysUserEntity user);
}
