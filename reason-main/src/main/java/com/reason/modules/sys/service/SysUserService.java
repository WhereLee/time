package com.reason.modules.sys.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.sys.entity.SysLoginEntity;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.form.PasswordForm;
import com.reason.modules.sys.form.SysLoginForm;
import com.reason.modules.sys.form.SysUserForm;
import com.reason.modules.sys.vo.SysPasswordVO;
import com.reason.modules.sys.vo.SysUserVO;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @date 2020-04-22 14:30:49
 */
public interface SysUserService extends IService<SysUserEntity> {

    /**
     * 查看初始配置
     * @return
     */
    Map<String, Object> getInitParam();

    /**
     * 登录  账号+验证码(企业微信|短信)
     * @param form
     */
    SysLoginEntity login(SysLoginForm form, String ip);

    /**
     * 退出
     * @param userId
     */
    void logout(Long userId);

    /**
     * 查询是否强制变更密码
     * @param user
     * @return 1：未超期  2-提醒变更  3-强制变更
     */
    Integer getChangeForce(SysUserEntity user);

    /**
     * 分页查询用户列表
     * @param form
     * @return
     */
    PageUtils queryPage(SysUserForm form);

    /**
     * 查询用户信息-下拉查询
     * @param form
     * @return
     */
    List<SysUserEntity> queryUser(SysUserForm form);

    /**
     * 根据ID 获取用户信息（包括角色信息）
     * @param userId
     * @return
     */
    SysUserEntity getInfo(Long userId);

    /**
     * 保存用户 && 用户角色权限
     * @param userVO
     * @param creator 操作员
     */
    void saveUser(SysUserVO userVO,SysUserEntity creator);

    /**
     * 修改用户 && 用户角色权限
     * @param userVO
     * @param creator 操作员
     */
    void updateUser(SysUserVO userVO,SysUserEntity creator);

    /**
     * 删除用户（逻辑）
     * @param creator 操作员
     * @param userId
     */
    void deleteUser(Long userId,SysUserEntity creator);

    /**
     * 打开或关闭用户
     * @param user
     */
    void openOrClose(SysUserEntity user, SysUserEntity creator);

    /**
     * 重置密码
     * @param passwordVO
     */
    void reset(SysPasswordVO passwordVO, SysUserEntity creator);

    /**
     * 修改密码
     * @param loginUser       登录用户
     * @param form     原密码 和 新密码
     */
    void updatePassword(SysUserEntity loginUser, PasswordForm form);
}

