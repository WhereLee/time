/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.annotation.DataFilter;
import com.reason.common.exception.RRException;
import com.reason.common.tree.MenuTreeUtils;
import com.reason.common.utils.*;
import com.reason.common.validator.Assert;
import com.reason.common.validator.ValidatorUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import com.reason.modules.sys.dao.*;
import com.reason.modules.sys.entity.SysMenuEntity;
import com.reason.modules.sys.entity.SysRoleEntity;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.form.SysRoleForm;
import com.reason.modules.sys.service.SysRoleMenuService;
import com.reason.modules.sys.service.SysRoleService;
import com.reason.modules.sys.vo.SysRoleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色
 *
 * @author Mark sunlightcs@gmail.com
 */
@Slf4j
@Service("sysRoleService")
public class SysRoleServiceImpl extends ServiceImpl<SysRoleDao, SysRoleEntity> implements SysRoleService {
	@Autowired
	private SysRoleMenuService sysRoleMenuService;
	@Autowired
	private SysRoleMenuDao sysRoleMenuDao;
	@Autowired
	private SysMenuDao sysMenuDao;
    @Autowired
	private SysUserRoleDao sysUserRoleDao;
    @Autowired
	private SysUserDao sysUserDao;
    @Autowired
	private SysRoleDao sysRoleDao;

	/**
	 * 分页查询角色
	 * @param form
	 * @RoleFilter(roleFilter = true) 角色数据权限限制
	 * @return
	 */
	@Override
	@DataFilter(roleFilter = true)
	public PageUtils queryPage(SysRoleForm form) {
		IPage<SysRoleEntity> page = this.page(
			new Query<SysRoleEntity>().getPage(new MapUtils()
					.put(Constant.PAGE,form.getPage()).put(Constant.LIMIT,form.getLimit())),
			new QueryWrapper<SysRoleEntity>()
					.eq("role_status", 0)
					.ne("role_id",Constant.DEVELOPER_ROLEID)
					.like(StringUtils.isNotBlank(form.getRoleName()),"role_name", form.getRoleName())
					.like(StringUtils.isNotBlank(form.getRoleComment()),"role_comment", form.getRoleComment())
					.apply(form.getSqlFilter() != null, form.getSqlFilter())
		);

		return new PageUtils(page);
	}

	/**
	 * 不分页查询角色-下拉查询
	 * 开发员和系统管理员查询所有角色，其他管理员查询所拥有的角色权限
	 * @RoleFilter(roleFilter = true) 角色数据权限限制
	 * @param form
	 * @return
	 */
	@Override
	@DataFilter(roleFilter = true)
    public List<SysRoleEntity> queryRole(SysRoleForm form) {
		List<SysRoleEntity> list = this.list(
				new QueryWrapper<SysRoleEntity>()
						.eq("role_status", 0)
						.ne("role_id",Constant.DEVELOPER_ROLEID)
						.like(StringUtils.isNotBlank(form.getRoleName()),"role_name", form.getRoleName())
						.like(StringUtils.isNotBlank(form.getRoleComment()),"role_comment", form.getRoleComment())
						.apply(form.getSqlFilter() != null, form.getSqlFilter())
		);

		return list;
	}

	/**
	 * 根据ID 获取角色信息信息（包括菜单信息和区域信息）
	 * @param roleId
	 * @return
	 */
	@Override
	public SysRoleEntity getInfo(Long roleId) {
		//1.查询角色信息
		SysRoleEntity role = this.getById(roleId);

		if (role == null || !role.isValid()) {
			throw new RRException("该角色信息不存在或已删除");
		}

		//2.角色对应的菜单
		List<SysMenuEntity> menuList = sysMenuDao.queryMenuByRoleId(roleId);
		//转换成Tree
		List<SysMenuEntity> menuTree = MenuTreeUtils.listToTree(menuList);
		role.setMenuList(menuTree);

		//4.创建人
		SysUserEntity user = sysUserDao.selectById(role.getRoleCreator());
		if (user != null && user.getUserId() != Constant.DEVELOPER_USERID) {
			role.setCreatorName(user.getUserName());
		}

		return role;
	}

	/**
	 * 保存角色 && 角色菜单权限 && 角色区域权限
	 * @param roleVO
	 * @param user 操作人
	 */
	@Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(SysRoleVO roleVO, SysUserEntity user) {
		//1.校验参数
		ValidatorUtils.validateEntity(roleVO,AddGroup.class);

		//2.创建角色实体类
		SysRoleEntity role = new SysRoleEntity(roleVO,1,user.getUserId());

		//3.检查权限是否越权
		checkPrems(role,user);

		//4.保存角色
        this.save(role);

        //5.保存角色菜单权限
        sysRoleMenuService.saveOrUpdate(role.getRoleId(),role.getMenuIdList());

	}

	/**
	 * 修改角色（更新创建人） && 角色菜单权限 && 角色区域权限
	 * @param roleVO
	 * @param user 操作人
	 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRoleVO roleVO, SysUserEntity user) {
		//1.校验参数
		ValidatorUtils.validateEntity(roleVO,UpdateGroup.class);

		//2.校验 id 是否存在
		SysRoleEntity old = this.getById(roleVO.getRoleId());
		if (old == null || !old.isValid()) {
			throw new RRException("该角色信息不存在或已删除");
		}

		//2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!user.devOrSysAdmin() && (old.getRoleCreator().compareTo(user.getUserId()) != 0))
			throw new RRException("没有权限");

		//2023年2月25日 不能修改、删除自己的角色
		//查询用户所拥有的角色权限
		List<Long> userRoleIdList = sysRoleDao.queryRoleIdByUserId(user.getUserId());
		if (userRoleIdList != null && userRoleIdList.size() != 0 && userRoleIdList.contains(roleVO.getRoleId()))
			throw new RRException("没有权限!");

		//2.创建角色实体类
		SysRoleEntity role = new SysRoleEntity(roleVO,2,null);

		//3.检查权限是否越权
		checkPrems(role,user);

		//4.修改角色
		this.updateById(role);

        //5.更新角色与菜单关系
        sysRoleMenuService.saveOrUpdate(role.getRoleId(), role.getMenuIdList());

	}

	/**
	 * 修改角色基本信息
	 * @param roleVO
	 * @param user
	 */
	@Override
	public void updateRoleBase(SysRoleVO roleVO, SysUserEntity user) {
		//1.校验参数
		ValidatorUtils.validateEntity(roleVO,UpdateGroup.class);

		//2.校验 id 是否存在
		SysRoleEntity old = this.getById(roleVO.getRoleId());
		if (old == null || !old.isValid()) {
			throw new RRException("该角色信息不存在或已删除");
		}

		//2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!user.devOrSysAdmin() && (old.getRoleCreator().compareTo(user.getUserId()) != 0))
			throw new RRException("没有权限");

		//2023年2月25日 不能修改、删除自己的角色
		//查询用户所拥有的角色权限
		List<Long> userRoleIdList = sysRoleDao.queryRoleIdByUserId(user.getUserId());
		if (userRoleIdList != null && userRoleIdList.size() != 0 && userRoleIdList.contains(roleVO.getRoleId()))
			throw new RRException("没有权限!");

		//3.创建角色实体类
		SysRoleEntity role = new SysRoleEntity(roleVO,2,null);

		//4.修改角色
		this.updateById(role);

	}

	/**
	 * 修改角色菜单权限
	 * @param roleVO
	 * @param user
	 */
	@Override
	public void updateRoleMenu(SysRoleVO roleVO, SysUserEntity user) {
		//1.校验参数
		ValidatorUtils.validateEntity(roleVO,UpdateGroup.class);

		//2.校验 id 是否存在
		SysRoleEntity old = this.getById(roleVO.getRoleId());
		if (old == null || !old.isValid()) {
			throw new RRException("该角色信息不存在或已删除");
		}

		//2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!user.devOrSysAdmin() && (old.getRoleCreator().compareTo(user.getUserId()) != 0))
			throw new RRException("没有权限");

		//2023年2月25日 不能修改、删除自己的角色
		//查询用户所拥有的角色权限
		List<Long> userRoleIdList = sysRoleDao.queryRoleIdByUserId(user.getUserId());
		if (userRoleIdList != null && userRoleIdList.size() != 0 && userRoleIdList.contains(roleVO.getRoleId()))
			throw new RRException("没有权限!");

		//3.创建角色实体类
		SysRoleEntity role = new SysRoleEntity(roleVO,2,null);

		//4.检查权限是否越权
		checkPrems(role,user);

		//5.更新角色与菜单关系
		sysRoleMenuService.saveOrUpdate(role.getRoleId(), role.getMenuIdList());

	}

	/**
	 * 删除角色（逻辑） && 角色菜单权限 && 角色区域权限
	 * @param roleId
	 * @param user
	 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId, SysUserEntity user) {
		//1.校验参数
		Assert.isNull(roleId,"角色ID不能为空");

		//2.校验 id 是否存在
		SysRoleEntity old = this.getById(roleId);
		if (old == null || !old.isValid()) {
			throw new RRException("该角色信息不存在或已删除");
		}

		//2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
		if (!user.devOrSysAdmin() && (old.getRoleCreator().compareTo(user.getUserId()) != 0))
			throw new RRException("没有权限");

		//2023年2月25日 不能修改、删除自己的角色
		//查询用户所拥有的角色权限
		List<Long> userRoleIdList = sysRoleDao.queryRoleIdByUserId(user.getUserId());
		if (userRoleIdList != null && userRoleIdList.size() != 0 && userRoleIdList.contains(roleId))
			throw new RRException("没有权限!");

		//3.删除角色 status = id
		this.updateById(new SysRoleEntity(roleId));

        //4.删除角色与菜单关联
		sysRoleMenuDao.deleteByMap(new MapUtils().put("role_id",roleId));

		//6.删除角色与用户关联
		sysUserRoleDao.deleteByMap(new MapUtils().put("role_id",roleId));
	}

	/**
	 * 检查权限是否越权
	 * @param role 实体
	 * @param user 操作人
	 */
	private void checkPrems(SysRoleEntity role, SysUserEntity user){
		//如果不是开发员，则需要判断角色的权限是否超过自己的权限
		if(user.developer()){
			return ;
		}

		//查询用户所拥有的菜单列表
		List<Long> menuIdList = sysMenuDao.queryMenuIdByUserId(user.getUserId());
		//判断是否越权
		if ((role.getMenuIdList() != null) && (!menuIdList.containsAll(role.getMenuIdList()))) {
			throw new RRException("新增角色的菜单权限，已超出你的权限范围");
		}

	}
}
