/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.modules.sys.entity.SysMenuEntity;
import com.reason.modules.sys.form.SysMenuForm;
import com.reason.modules.sys.vo.SysMenuVO;

import java.util.List;


/**
 * 菜单管理
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysMenuService extends IService<SysMenuEntity> {

	/**
	 * 查询导航菜单
	 * 登录用户所有的菜单权限
	 * @param form
	 * @return
	 */
	List<SysMenuEntity> queryNav(SysMenuForm form);

	/**
	 * 查询菜单信息-不分页
	 * @param form
	 * @return
	 */
	List<SysMenuEntity> queryList(SysMenuForm form);

	/**
	 * 查询菜单信息-下拉查询
	 * 登录用户所有的菜单权限
	 * @param form
	 * @return
	 */
	List<SysMenuEntity> queryMenu(SysMenuForm form);

	/**
	 * 根据ID 查询菜单详细信息
	 * @param menuId
	 * @return
	 */
	SysMenuEntity getInfo(Long menuId);

	/**
	 * 新增菜单
	 * @param menuVO
	 */
	void saveMenu(SysMenuVO menuVO);

	/**
	 * 修改菜单
	 * @param menuVO
	 */
	void updateMenu(SysMenuVO menuVO);

	/**
	 * 删除菜单（包括下级） && 角色菜单
	 */
	void delete(Long menuId);

}
