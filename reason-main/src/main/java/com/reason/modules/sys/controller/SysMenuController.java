/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Constant;
import com.reason.common.utils.Result;
import com.reason.modules.sys.entity.SysMenuEntity;
import com.reason.modules.sys.form.SysMenuForm;
import com.reason.modules.sys.service.SysMenuService;
import com.reason.modules.sys.vo.SysMenuVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统菜单
 *
 * @author Mark sunlightcs@gmail.com
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController extends AbstractController {
	@Autowired
	private SysMenuService sysMenuService;

	/**
	 * 导航菜单——Tree
	 */
	@Operation(summary = "导航菜单", description = "登录后获取导航菜单-不需要查询参数-返回Tree；权限说明：不需要权限")
	@ApiOperationSupport(order = 1)
	@GetMapping("/nav")
	public Result<List<SysMenuEntity>> nav(){
		List<SysMenuEntity> menuTree = sysMenuService.queryNav(new SysMenuForm());

		return Result.ok(menuTree);
	}
	
	/**
	 * 所有菜单列表——Tree
	 */
	@Operation(summary = "列表查询", description = "查询所有菜单信息-不分页-返回Tree；权限说明：sys:menu:list 查询")
	@ApiOperationSupport(order = 10,ignoreParameters = {"page","limit","areaId","sqlFilter","menuFid"})
	@SysLog(module = "菜单模块",func = "查询",value = "列表查询菜单")
	@GetMapping("/list")
	@PreAuthorize("hasAuthority('sys:menu:list')")
	public Result<List<SysMenuEntity>> list(SysMenuForm form){
		List<SysMenuEntity> menuTree = sysMenuService.queryList(form);

		return Result.ok(menuTree);
	}
	
	/**
	 * 菜单信息
	 * 菜单下拉选择用
	 * 登录用户所有的菜单权限
	 */
	@Operation(summary = "下拉查询", description = "菜单下拉框显示时调用-不分页-返回List；权限说明：sys:menu:select 查询")
	@ApiOperationSupport(order = 20,ignoreParameters = {"page","limit","areaId","sqlFilter"})
	@SysLog(module = "菜单模块",func = "查询",value = "下拉查询菜单")
	@GetMapping("/select")
	@PreAuthorize("hasAuthority('sys:menu:select')")
	public Result<List<SysMenuEntity>> select(SysMenuForm form){
		List<SysMenuEntity> menuList = sysMenuService.queryMenu(form);

		return Result.ok(menuList);
	}
	
	/**
	 * 菜单信息
	 */
	@Operation(summary = "详细查询", description = "查询所选菜单详细信息；权限说明：sys:menu:info 查询")
	@ApiOperationSupport(order = 30)
	@SysLog(module = "菜单模块",func = "查询",value = "查询菜单详细")
	@GetMapping("/info/{menuId}")
	@PreAuthorize("hasAuthority('sys:menu:info')")
	public Result<SysMenuEntity> info(@PathVariable("menuId") Long menuId){
		SysMenuEntity menu = sysMenuService.getInfo(menuId);

		return Result.ok(menu);
	}
	
	/**
	 * 新增菜单
	 */
	@Operation(summary = "新增接口", description = "新增菜单，权限说明：sys:menu:save 新增")
	@ApiOperationSupport(order = 40,ignoreParameters = {"menuId"})
	@SysLog(module = "菜单模块",func = "新增",value = "新增菜单")
	@PostMapping("/save")
	@PreAuthorize("hasAuthority('sys:menu:save')")
	public Result save(@RequestBody SysMenuVO menuVO){
		//只有开发员有新增、修改、删除权限
		if (!getUser().developer()) {
			throw new RRException("没有权限");
		}
		sysMenuService.saveMenu(menuVO);
		
		return Result.ok();
	}
	
	/**
	 * 修改菜单
	 */
	@Operation(summary = "修改接口", description = "修改菜单，权限说明：sys:menu:update 修改")
	@ApiOperationSupport(order = 50)
	@SysLog(module = "菜单模块",func = "修改",value = "修改菜单")
	@PostMapping("/update")
	@PreAuthorize("hasAuthority('sys:menu:update')")
	public Result update(@RequestBody SysMenuVO menuVO){
		//只有开发员有新增、修改、删除权限
		if (!getUser().developer()) {
			throw new RRException("没有权限");
		}
		sysMenuService.updateMenu(menuVO);
		
		return Result.ok();
	}
	
	/**
	 * 删除菜单
	 */
	@Operation(summary = "删除接口", description = "删除菜单，权限说明：sys:menu:delete 删除")
	@ApiOperationSupport(order = 60)
	@SysLog(module = "菜单模块",func = "删除",value = "删除菜单")
	@PostMapping("/delete/{menuId}")
	@PreAuthorize("hasAuthority('sys:menu:delete')")
	public Result delete(@PathVariable("menuId") Long menuId){
		//只有开发员有新增、修改、删除权限
		if (!getUser().developer()) {
			throw new RRException("没有权限");
		}
		sysMenuService.delete(menuId);

		return Result.ok();
	}
}
