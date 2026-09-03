/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.sys.entity.SysMenuEntity;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 菜单管理
 *
 * @author Mark sunlightcs@gmail.com
 */
@Repository
public interface SysMenuDao extends BaseMapper<SysMenuEntity> {
	/**
	 * 查询用户的所有权限
	 * @param userId  用户ID
	 */
	List<String> queryPermsByUserId(Long userId);

	/**
	 * 查询角色对应的菜单权限
	 * @param roleId
	 * @return
	 */
	List<SysMenuEntity> queryMenuByRoleId(Long roleId);

	/**
	 * 查询用户所拥有的菜单ID
	 * @param userId
	 * @return
	 */
	List<Long> queryMenuIdByUserId(Long userId);

	/**
	 * 根据ID 查询所有下级菜单ID（包括本级）
	 * @param menuId
	 * @return
	 */
	List<Long> queryMenuIdById(Long menuId);

	/**
	 * 根据菜单ID 修改所有下级菜单的fids
	 * @param oldFids 修改前父级的fids，拼接了“,”
	 * @param newFids 修改后父级的fids，拼接了“,”
	 * @param menuId 父级的ID
	 */
	void updateSubMenuById(
			@Param("oldFids") String oldFids,
			@Param("newFids") String newFids,
			@Param("menuId") Long menuId);

}
