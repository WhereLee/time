/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.modules.sys.entity.SysRoleMenuEntity;

import java.util.List;



/**
 * 角色与菜单对应关系
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysRoleMenuService extends IService<SysRoleMenuEntity> {
	/**
	 * 新增/修改角色菜单
	 * @param roleId
	 * @param menuIdList
	 */
	void saveOrUpdate(Long roleId, List<Long> menuIdList);
	
}
