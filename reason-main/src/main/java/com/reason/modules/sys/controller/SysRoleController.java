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
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Result;
import com.reason.modules.sys.entity.SysRoleEntity;
import com.reason.modules.sys.form.SysRoleForm;
import com.reason.modules.sys.service.SysRoleService;
import com.reason.modules.sys.vo.SysRoleVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理
 *
 * @author Mark sunlightcs@gmail.com
 */
@Tag(name = "角色管理")
@Slf4j
@RestController
@RequestMapping("/sys/role")
public class SysRoleController extends AbstractController {
	@Autowired
	private SysRoleService sysRoleService;

	/**
	 * 角色列表-分页
	 */
	@Operation(summary = "列表查询", description = "查询所有角色信息-分页；权限说明：sys:role:list 查询")
	@ApiOperationSupport(order = 1,ignoreParameters = {"areaId","sqlFilter"})
	@SysLog(module = "角色管理", func = "查询", value = "列表查询角色")
	@GetMapping("/list")
	@RequiresPermissions("sys:role:list")
	public Result<PageUtils> list(SysRoleForm form){
		PageUtils page = sysRoleService.queryPage(form);

		return Result.ok(page);
	}
	
	/**
	 * 角色列表-不分页
	 * 角色下拉选择用
	 * 开发员和系统管理员查询所有角色，其他管理员查询所拥有的角色权限
	 */
	@Operation(summary = "下拉查询", description = "角色下拉框显示时调用-不分页-返回List；权限说明：sys:role:select 查询")
	@ApiOperationSupport(order = 10,ignoreParameters = {"page","limit","areaId","sqlFilter"})
	@SysLog(module = "角色管理", func = "查询", value = "下拉查询角色")
	@GetMapping("/select")
	@RequiresPermissions("sys:role:select")
	public Result<List<SysRoleEntity>> select(SysRoleForm form){
		List<SysRoleEntity> list = sysRoleService.queryRole(form);

		return Result.ok(list);
	}
	
	/**
	 * 角色信息
	 */
	@Operation(summary = "详细查询", description = "查询所选角色详细信息；权限说明：sys:role:info 查询")
	@ApiOperationSupport(order = 20)
	@SysLog(module = "角色管理", func = "查询", value = "查询角色详细")
	@GetMapping("/info/{roleId}")
	@RequiresPermissions("sys:role:info")
	public Result<SysRoleEntity> info(@PathVariable("roleId") Long roleId){
		SysRoleEntity role = sysRoleService.getInfo(roleId);

		return Result.ok(role);
	}
	
	/**
	 * 新增角色 && 角色菜单 && 角色区域
	 */
	@Operation(summary = "新增接口", description = "新增角色，权限说明：sys:role:save 新增")
	@ApiOperationSupport(order = 40,ignoreParameters = {"roleId"})
	@SysLog(module = "角色管理", func = "新增", value = "新增角色")
	@PostMapping("/save")
	@RequiresPermissions("sys:role:save")
	public Result save(@RequestBody SysRoleVO roleVO){
		/*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!getUser().devOrSysAdmin()) {
			throw new RRException("没有权限");
		}*/
		sysRoleService.saveRole(roleVO,getUser());
		
		return Result.ok();
	}
	
	/**
	 * 修改角色 && 角色菜单
	 * 修改角色可能
	 */
	@Operation(summary = "修改接口", description = "修改角色（可同时设置菜单权限），权限说明：sys:role:update 修改")
	@ApiOperationSupport(order = 50)
	@SysLog(module = "角色管理", func = "修改", value = "修改角色")
	@PostMapping("/update")
	@RequiresPermissions("sys:role:update")
	public Result update(@RequestBody SysRoleVO roleVO){
		/*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!getUser().devOrSysAdmin()) {
			throw new RRException("没有权限");
		}*/
		sysRoleService.updateRole(roleVO,getUser());
		
		return Result.ok();
	}

	/**
	 * 将角色修改接口分成2个接口：角色基本信息修改接口、角色菜单权限修改接口
	 * 2020年8月11日
	 * @param roleVO
	 * @return
	 */
	@Operation(summary = "修改接口-基本信息", description = "修改角色基本信息（不包括权限），权限说明：sys:role:update 修改")
	@ApiOperationSupport(order = 52,ignoreParameters = {"menuIdList","regionIdList"})
	@SysLog(module = "角色管理", func = "修改", value = "修改角色基本信息")
	@PostMapping("/updateRoleBase")
	@RequiresPermissions("sys:role:update")
	public Result updateRoleBase(@RequestBody SysRoleVO roleVO){
		/*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!getUser().devOrSysAdmin()) {
			throw new RRException("没有权限");
		}*/
		sysRoleService.updateRoleBase(roleVO,getUser());

		return Result.ok();
	}

	/**
	 * 将角色修改接口分成2个接口：角色基本信息修改接口、角色菜单权限修改接口
	 * 2020年8月11日
	 * @param roleVO
	 * @return
	 */
	@Operation(summary = "修改接口-菜单权限", description = "修改角色菜单权限，权限说明：sys:role:update 修改")
	@ApiOperationSupport(order = 54,ignoreParameters = {"roleName","roleComment","areaIdList"})
	@SysLog(module = "角色管理", func = "修改", value = "修改角色菜单权限")
	@PostMapping("/updateRoleMenu")
	@RequiresPermissions("sys:role:update")
	public Result updateRoleMenu(@RequestBody SysRoleVO roleVO){
		/*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!getUser().devOrSysAdmin()) {
			throw new RRException("没有权限");
		}*/
		sysRoleService.updateRoleMenu(roleVO,getUser());

		return Result.ok();
	}
	
	/**
	 * 删除角色
	 */
	@Operation(summary = "删除接口", description = "删除角色，权限说明：sys:role:delete 删除")
	@ApiOperationSupport(order = 60)
	@SysLog(module = "角色管理", func = "删除", value = "删除角色")
	@PostMapping("/delete/{roleId}")
	@RequiresPermissions("sys:role:delete")
	public Result delete(@PathVariable("roleId") Long roleId){
		/*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!getUser().devOrSysAdmin()) {
			throw new RRException("没有权限");
		}*/
		sysRoleService.deleteRole(roleId,getUser());
		
		return Result.ok();
	}
}
