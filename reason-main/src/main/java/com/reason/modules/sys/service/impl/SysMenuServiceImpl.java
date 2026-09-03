/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.annotation.DataFilter;
import com.reason.common.exception.RRException;
import com.reason.common.tree.MenuTreeUtils;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.StringUtils;
import com.reason.common.validator.Assert;
import com.reason.common.validator.ValidatorUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import com.reason.modules.sys.dao.SysRoleMenuDao;
import com.reason.modules.sys.form.SysMenuForm;
import com.reason.modules.sys.service.SysMenuService;
import com.reason.modules.sys.dao.SysMenuDao;
import com.reason.modules.sys.entity.SysMenuEntity;
import com.reason.modules.sys.vo.SysMenuVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service("sysMenuService")
public class SysMenuServiceImpl extends ServiceImpl<SysMenuDao, SysMenuEntity> implements SysMenuService {
	@Autowired
	private SysMenuDao sysMenuDao;
	@Autowired
	private SysRoleMenuDao sysRoleMenuDao;

	/**
	 * 查询导航菜单
	 * 登录用户所有的菜单权限
	 * @DataFilter 菜单数据权限
	 * @param form
	 * @return
	 */
	@Override
	@DataFilter(menuFilter = true)
	public List<SysMenuEntity> queryNav(SysMenuForm form) {
		//1.查询菜单信息
		List<SysMenuEntity> menuList = this.list(
				new QueryWrapper<SysMenuEntity>()
						.eq("menu_status", 0)
						.apply(form.getSqlFilter() != null, form.getSqlFilter())
						.orderByAsc("menu_origin")
						.orderByDesc("menu_ordernum")
		);

		//2.转换成tree
		List<SysMenuEntity> menuTree = MenuTreeUtils.listToTree(menuList);

		return menuTree;
	}

	/**
	 * 查询菜单信息-不分页-Tree
	 * @param form
	 * @return
	 */
	@Override
	@DataFilter(menuFilter = true)
	public List<SysMenuEntity> queryList(SysMenuForm form) {
		//1.查询菜单列表
		List<SysMenuEntity> menuList = this.list(
				new QueryWrapper<SysMenuEntity>()
						.eq("menu_status", 0)
						.like(StringUtils.isNotBlank(form.getMenuName()),"menu_name", form.getMenuName())
						.eq(form.getMenuType() != null,"menu_type",form.getMenuType())
						.apply(form.getSqlFilter() != null, form.getSqlFilter())
						.orderByAsc("menu_origin")
						.orderByDesc("menu_ordernum")
		);

		for (SysMenuEntity item : menuList) {
			//查询父级菜单名称
			if (item.getMenuFid() != 0) {
				SysMenuEntity menu = this.getInfo(item.getMenuFid());
				if (menu != null) {
					item.setMenuFidName(menu.getMenuName());
				}
			}
		}

		//2.转换成tree
		List<SysMenuEntity> menuTree = MenuTreeUtils.listToTree(menuList);

		return menuTree;
	}

	/**
	 * 查询菜单信息-下拉查询
	 * 登录用户所有的菜单权限
	 * @param form
	 * @return
	 */
	@Override
	@DataFilter(menuFilter = true)
	public List<SysMenuEntity> queryMenu(SysMenuForm form) {
		Assert.isNull(form.getMenuFid(),"父级ID不能为空");

		List<SysMenuEntity> menuList = this.list(
				new QueryWrapper<SysMenuEntity>()
						.eq("menu_status", 0)
						.like(StringUtils.isNotBlank(form.getMenuName()),"menu_name", form.getMenuName())
						.eq(form.getMenuType() != null,"menu_type",form.getMenuType())
						.eq("menu_fid",form.getMenuFid())
						.apply(form.getSqlFilter() != null, form.getSqlFilter())
						.orderByAsc("menu_origin")
						.orderByDesc("menu_ordernum")
		);

		return menuList;
	}

	/**
	 * 根据ID 查询菜单详细信息
	 * @param menuId
	 * @return
	 */
	@Override
	public SysMenuEntity getInfo(Long menuId) {
		//1.查询菜单信息
		SysMenuEntity menu = this.getById(menuId);

		if (menu == null || !menu.isValid()) {
			throw new RRException("该菜单信息不存在或已删除");
		}

		//2.查询父级菜单
		SysMenuEntity parent = this.getById(menu.getMenuFid());
		if (parent != null) {
			menu.setMenuFidName(parent.getMenuName());
		}

		return menu;
	}

	/**
	 * 新增菜单
	 * @param menuVO
	 */
	@Override
	public void saveMenu(SysMenuVO menuVO) {
		//1.数据校验
		ValidatorUtils.validateEntity(menuVO,AddGroup.class);

		//2.创建菜单实体类
		SysMenuEntity menu = new SysMenuEntity(menuVO,1);

		//2.校验fid 获取fids
		String fids = "0";
		if (menu.getMenuFid() == null) {//fid 如果null，后台默认没有父级
			menu.setMenuFid(0L);
		}
		if (menu.getMenuFid() != 0){//有父级
			SysMenuEntity parent =  this.getById(menu.getMenuFid());
			if (parent == null || !parent.isValid()) {
				throw new RRException("该父级菜单信息不存在或已删除");
			}
			fids = parent.getMenuFids()+","+parent.getMenuId();
		}
		menu.setMenuFids(fids);

		//3.新增菜单
		this.save(menu);
	}

	/**
	 * 修改菜单
	 * @param menuVO
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateMenu(SysMenuVO menuVO) {
		//1.数据校验
		ValidatorUtils.validateEntity(menuVO,UpdateGroup.class);

		//2.创建菜单实体类
		SysMenuEntity menu = new SysMenuEntity(menuVO,2);

		//2.校验 id 是否存在
		SysMenuEntity old = this.getById(menu.getMenuId());
		if (old == null || !old.isValid()) {
			throw new RRException("该菜单信息不存在或已删除");
		}

		//3.校验fid 获取fids
		String fids = "0";
		if (menu.getMenuFid() == null) {//fid 如果null，后台默认没有父级
			menu.setMenuFid(0L);
		}
		if (menu.getMenuFid() != 0){//有父级
			SysMenuEntity parent =  this.getById(menu.getMenuFid());
			if (parent == null || !parent.isValid()) {
				throw new RRException("该父级菜单信息不存在或已删除");
			}
			fids = parent.getMenuFids()+","+parent.getMenuId();
		}
		menu.setMenuFids(fids);

		//4.修改菜单
		this.updateById(menu);

		//5.修改下级菜单的fids
		if (!old.getMenuFids().equals(menu.getMenuFids())) {
			sysMenuDao.updateSubMenuById(old.getMenuFids() + ",", menu.getMenuFids() + ",", menu.getMenuId());
		}
	}

	/**
	 * 删除菜单（包括下级） && 角色菜单
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delete(Long menuId){
		//1.校验参数
		Assert.isNull(menuId,"菜单ID不能为空");

		//2.校验 id 是否存在
		SysMenuEntity old = this.getById(menuId);
		if (old == null || !old.isValid()) {
			throw new RRException("该菜单信息不存在或已删除");
		}

		//3.查询所有下级菜单（包括本级菜单）
		List<Long> menuIdList = sysMenuDao.queryMenuIdById(menuId);

		for (Long item : menuIdList) {
			//4.逻辑批量删除 status = id
			this.updateById(new SysMenuEntity(item));

			//5.物理批量删除角色菜单
			sysRoleMenuDao.deleteByMap(new MapUtils().put("menu_id",item));
		}
	}

}
