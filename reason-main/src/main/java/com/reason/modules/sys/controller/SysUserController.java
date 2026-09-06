package com.reason.modules.sys.controller;


import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Result;
import com.reason.modules.sys.form.PasswordForm;
import com.reason.modules.sys.form.SysUserForm;
import com.reason.modules.sys.vo.SysPasswordVO;
import com.reason.modules.sys.vo.SysUserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.service.SysUserService;
import com.reason.common.utils.PageUtils;

import java.util.List;


/**
 * 
 *
 * @date 2020-04-22 14:30:49
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("sys/user")
public class SysUserController extends AbstractController{
    @Autowired
    private SysUserService sysUserService;

    /**
     * 用户列表-分页
     */
    @Operation(summary = "列表查询", description = "查询所有用户信息-分页；权限说明：sys:user:list 查询")
    @ApiOperationSupport(order = 1,ignoreParameters = {"areaId","sqlFilter"})
    @SysLog(module = "用户管理",func = "查询",value = "列表查询用户")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public Result<PageUtils> list(SysUserForm form){
        PageUtils page = sysUserService.queryPage(form);

        return Result.ok(page);
    }

    /**
     * 用户信息
     * 用户下拉选择用
     */
    @Operation(summary = "下拉查询", description = "用户下拉框显示时调用-不分页-返回List，权限说明：sys:user:select 查询")
    @ApiOperationSupport(order = 10,ignoreParameters = {"page","limit","areaId","sqlFilter"})
    @SysLog(module = "用户管理",func = "查询",value = "下拉查询用户")
    @GetMapping("/select")
    @PreAuthorize("hasAuthority('sys:user:select')")
    public Result<List<SysUserEntity>> select(SysUserForm form){
        List<SysUserEntity> areaList = sysUserService.queryUser(form);

        return Result.ok(areaList);
    }


    /**
     * 用户信息
     */
    @Operation(summary = "详细查询", description = "查询所选用户详细信息；权限说明：sys:user:info 查询")
    @ApiOperationSupport(order = 20)
    @SysLog(module = "用户管理",func = "查询",value = "查询用户详细")
    @GetMapping("/info/{userId}")
    @PreAuthorize("hasAuthority('sys:user:info')")
    public Result<SysUserEntity> info(@PathVariable("userId") Long userId){
		SysUserEntity user = sysUserService.getInfo(userId);

        return Result.ok(user);
    }

    /**
     * 新增用户 && 用户角色
     */
    @Operation(summary = "新增接口", description = "新增用户，权限说明：sys:user:save 新增")
    @ApiOperationSupport(order = 40,ignoreParameters = {"userId"})
    @SysLog(module = "用户管理",func = "新增",value = "新增用户")
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('sys:user:save')")
    public Result save(@RequestBody SysUserVO userVO){
        /*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!getUser().devOrSysAdmin()) {
            throw new RRException("没有权限");
        }*/
        sysUserService.saveUser(userVO,getUser());

        return Result.ok();
    }

    /**
     * 修改用户 && 用户角色
     * 密码不可修改-密码重置单独设置接口 reset
     */
    @Operation(summary = "修改接口", description = "修改用户，权限说明：sys:user:update 修改")
    @ApiOperationSupport(order = 50)
    @SysLog(module = "用户管理",func = "修改",value = "修改用户")
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('sys:user:update')")
    public Result update(@RequestBody SysUserVO userVO){
        /*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!getUser().devOrSysAdmin()) {
            throw new RRException("没有权限");
        }*/
        sysUserService.updateUser(userVO,getUser());

        return Result.ok();
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除接口", description = "删除用户，权限说明：sys:user:delete 删除")
    @ApiOperationSupport(order = 60)
    @SysLog(module = "用户管理",func = "删除",value = "删除用户")
    @PostMapping("/delete/{userId}")
    @PreAuthorize("hasAuthority('sys:user:delete')")
    public Result delete(@PathVariable("userId") Long userId){
        /*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!getUser().devOrSysAdmin()) {
            throw new RRException("没有权限");
        }*/
        sysUserService.deleteUser(userId,getUser());

        return Result.ok();
    }

    /**
     * 关闭用户-锁定
     */
    @Operation(summary = "锁定接口", description = "锁定用户，权限说明：sys:user:close 关闭")
    @ApiOperationSupport(order = 70)
    @SysLog(module = "用户管理", func = "关闭", value = "关闭用户")
    @PostMapping("/close/{userId}")
    @PreAuthorize("hasAuthority('sys:user:close')")
    public Result close(@PathVariable("userId") Long userId){
        sysUserService.openOrClose(new SysUserEntity(userId,1), getUser());

        return Result.ok();
    }

    /**
     * 开放用户-解锁
     */
    @Operation(summary = "解锁接口", description = "解锁用户，权限说明：sys:user:open 开放")
    @ApiOperationSupport(order = 80)
    @SysLog(module = "用户管理", func = "开放", value = "开放用户")
    @PostMapping("/open/{userId}")
    @PreAuthorize("hasAuthority('sys:user:open')")
    public Result open(@PathVariable("userId") Long userId){
        sysUserService.openOrClose(new SysUserEntity(userId,0), getUser());

        return Result.ok();
    }

    /**
     * 密码重置
     */
    @Operation(summary = "密码重置", description = "密码重置，权限说明：sys:user:update 修改")
    @ApiOperationSupport(order = 85)
    @SysLog(module = "用户管理",func = "密码重置",value = "密码重置")
    @PostMapping("/reset")
    @PreAuthorize("hasAuthority('sys:user:update')")
    public Result reset(@RequestBody SysPasswordVO passwordVO){
        /*//只有开发员或系统管理员有权限——2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!getUser().devOrSysAdmin()) {
            throw new RRException("没有权限");
        }*/
        sysUserService.reset(passwordVO, getUser());

        return Result.ok();
    }



    /**
     * 获取登录的用户信息
     */
    @Operation(summary = "查询接口", description = "个人信息查询-不需要查询条件，权限说明：不需要权限")
    @ApiOperationSupport(order = 90)
    @SysLog(module = "个人信息", func = "查询", value = "查询个人信息")
    @GetMapping("/info")
    public Result<SysUserEntity> info(){
        SysUserEntity user = sysUserService.getInfo(getUserId());

        return Result.ok(user);
    }

    /**
     * 修改登录用户密码
     */
    @Operation(summary = "修改接口", description = "修改密码，权限说明：不需要权限")
    @ApiOperationSupport(order = 90)
    @SysLog(module = "个人信息", func = "修改", value = "修改密码")
    @PostMapping("/password")
    public Result password(@RequestBody PasswordForm form){
        sysUserService.updatePassword(getUser(),form);

        return Result.ok();
    }

}
