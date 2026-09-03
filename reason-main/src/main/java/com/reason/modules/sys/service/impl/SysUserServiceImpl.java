package com.reason.modules.sys.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.reason.common.annotation.DataFilter;
import com.reason.common.exception.RRException;
import com.reason.common.utils.*;
import com.reason.common.validator.Assert;
import com.reason.common.validator.ValidatorUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import com.reason.modules.sys.dao.SysRoleDao;
import com.reason.modules.sys.dao.SysUserRoleDao;
import com.reason.modules.sys.entity.*;
import com.reason.modules.sys.form.PasswordForm;
import com.reason.modules.sys.form.SysLoginForm;
import com.reason.modules.sys.form.SysUserForm;
import com.reason.modules.sys.service.SysDictionaryService;
import com.reason.modules.sys.service.SysUserRoleService;
import com.reason.modules.sys.service.SysUserTokenService;
import com.reason.modules.sys.vo.SysPasswordVO;
import com.reason.modules.sys.vo.SysUserVO;
import lombok.extern.slf4j.Slf4j;
import com.reason.common.utils.PasswordCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.reason.modules.sys.dao.SysUserDao;
import com.reason.modules.sys.service.SysUserService;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service("sysUserService")
public class SysUserServiceImpl extends ServiceImpl<SysUserDao, SysUserEntity> implements SysUserService {
    @Autowired
    private ParamUtils paramUtils;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private SysRoleDao sysRoleDao;
    @Autowired
    private SysUserRoleDao sysUserRoleDao;
    @Autowired
    private SysUserRoleService sysUserRoleService;
    @Autowired
    private SysUserTokenService sysUserTokenService;
    @Autowired
    private SysDictionaryService sysDictionaryService;

    /**
     * 查看初始配置
     * @return
     */
    @Override
    public Map<String, Object> getInitParam() {
        Map<String, Object> map = new HashMap<>();

        //1.codeVerify 登录短信验证码验证  1-开启 2-关闭（默认关闭，短信/企业微信验证码能力已移除）
        map.put("codeVerify", false);

        //3.attemptLimit 口令最大尝试次数，超过则限时锁定账号  0-不做限制  默认不做限制
        //lockTime 账号限时锁定时间（单位：分钟） 默认5分钟
        Map<String, Integer> pwdMap = paramUtils.getAttemptLimtAndLockTime();
        map.put("attemptLimit", pwdMap.get("attemptLimit"));
        map.put("lockTime", pwdMap.get("lockTime"));

        return map;
    }

    /**
     * 登录  账号+验证码(企业微信|短信)
     * @param form
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysLoginEntity login(SysLoginForm form, String ip) {
        //0.IP黑白名单校验
        SysDicIplistEntity iplist = sysDictionaryService.getIpList();
        //black_list IP黑名单 多个IP英文逗号分隔 列表内IP禁止登录
        String blackIps = iplist.getBlackList();
        if (StringUtils.isNotBlank(blackIps)) {
            List<String> blackList = Arrays.asList(StringUtils.replaceBlank(blackIps).split(","));
            if (blackList.contains(ip))
                throw new RRException("黑名单IP，禁止登录");
        }
        //white_list IP白名单 多个IP英文逗号分隔 空时允许所有IP登录（黑名单除外），不为空时列表内IP才能登录
        String whiteIps = iplist.getWhiteList();
        if (StringUtils.isNotBlank(whiteIps)) {
            List<String> whiteList = Arrays.asList(StringUtils.replaceBlank(whiteIps).split(","));
            if (!whiteList.contains(ip))
                throw new RRException("不在白名单内，不能登录");
        }

        //当前时间戳
        Long timestamp = System.currentTimeMillis()/1000;
        //离当日结束（23:59:59）剩余时间
        Long timeRemaining = DateUtils.getEndTime(timestamp)-timestamp;
        //参数
        String loginname = form.getLoginname();
        String password = form.getPassword();
        //Redis Key
        String userKey = "user_"+loginname;
        //参数校验
        Assert.isBlank(loginname,"登录名不能为空");
        Assert.isBlank(password,"密码不能为空");

        //1.口令最大尝试次数校验  0-不做限制
        Map<String, Integer> map = paramUtils.getAttemptLimtAndLockTime();
        //口令最大尝试次数，超过则限时锁定账号  0-不做限制  默认不做限制
        Integer attemptLimt = map.get("attemptLimit");
        //账号限时锁定时间（单位：分钟） 默认5分钟
        Integer lockTime = map.get("lockTime");
        JSONObject userObj = null;
        //口令错误次数
        Integer pwdTimes = 0;
        if (attemptLimt != 0) {
            userObj = new JSONObject();
            if (redisUtils.hasKey(userKey)) {
                userObj = (JSONObject) redisUtils.getKeyValue(userKey);
                pwdTimes = userObj.getInteger("times");
                Boolean lock = userObj.getBoolean("lock");
                if (lock != null && lock)
                    throw new RRException("账号已限时锁定，请稍后再尝试");
            }
        }

        //2.用户信息
        SysUserEntity user = sysUserDao.getUserByLoginname(loginname);

        //2.1 账号不存在、密码错误（密码校验支持 BCrypt 与遗留 SHA-256 两种格式）
        if(user == null || !PasswordCodec.matches(password, user.getUserPassword(), user.getUserSalt())) {
            //口令最大尝试次数处理 错误次数+1
            if (attemptLimt != 0) {
                pwdTimes ++;
                userObj.put("times", pwdTimes);
                if (pwdTimes >= attemptLimt) {//锁定
                    userObj.put("lock", true);
                    //更新Redis数据
                    redisUtils.deleteKey(userKey);
                    redisUtils.setKeyValue(userKey, userObj, lockTime*60L);

                    throw new RRException("账号或密码连续错误"+attemptLimt+"次，账号已限时锁定，"+lockTime+"分钟后解锁");
                } else {
                    //更新Redis数据
                    redisUtils.deleteKey(userKey);
                    redisUtils.setKeyValue(userKey, userObj, timeRemaining);
                }
            }

            throw new RRException("账号或密码错误");
        }

        //2.2 账号锁定
        if(!user.open())
            throw new RRException("账号已锁定,请联系管理员");

        //登录成功 删除Key
        redisUtils.deleteKey(userKey);

        //渐进升级：遗留 SHA-256 密码在登录成功后重哈希为 BCrypt（下次登录起走 BCrypt 校验）
        if (PasswordCodec.isLegacy(user.getUserPassword())) {
            SysUserEntity upgrade = new SysUserEntity();
            upgrade.setUserId(user.getUserId());
            upgrade.setUserPassword(PasswordCodec.encode(password));
            this.updateById(upgrade);
        }

        //4.更新登录时间
        Long userPwdChangetime = null;
        if (user.getUserPwdChangetime() == null)
            userPwdChangetime = timestamp;
        this.updateById(new SysUserEntity(user.getUserId(), timestamp, ip, userPwdChangetime));

        //5.生成token，并保存到数据库
        SysLoginEntity login = sysUserTokenService.createToken(user.getUserId());
        //登录用户的userId
        login.setUserId(user.getUserId());
        //查询用户所拥有的角色权限
        List<Long> userRoleIdList = sysRoleDao.queryRoleIdByUserId(user.getUserId());
        login.setRoleIdList(userRoleIdList);

        //6.是否强制变更密码 1：未超期  2-提醒变更  3-强制变更
        Integer state = getChangeForce(user);
        if (state != 1)
            login.setMsg("登录成功，请变更密码");

        return login;
    }

    /**
     * 退出
     * @param userId
     */
    @Override
    public void logout(Long userId) {
        //退出，修改token值
        sysUserTokenService.updateToken(userId);
    }

    /**
     * 查询是否强制变更密码
     * @param user
     * @return 1：未超期  2-提醒变更  3-强制变更
     */
    @Override
    public Integer getChangeForce(SysUserEntity user) {
        //1.用户上次变更时间
        Long pwdChangetime = user.getUserPwdChangetime();
        if (pwdChangetime == null)
            return 1;

        //2.change_force 口令定期变更  1-强制变更 2-提醒变更 默认2
        //change_limit 口令变更时限（单位：天） 默认 30天
        Map<String, Integer> map = paramUtils.getChangeForceAndLimit();
        Integer changeLimit = map.get("changeLimit");
        Long timestamp = System.currentTimeMillis()/1000;
        if (pwdChangetime.compareTo(timestamp - changeLimit*24*60*60L) > 0)
            return 1;

        //change_force 口令定期变更  1-强制变更 2-提醒变更 默认2
        Integer changeForce = map.get("changeForce");
        if (changeForce == 1)
            return 3;

        return 2;

    }

    /**
     * 分页查询用户列表
     * 去除开发员用户
     * @param form
     * @return
     */
    @Override
    @DataFilter(userFilter = true)
    public PageUtils queryPage(SysUserForm form) {
        log.info("form1:{}",form);
        IPage<SysUserEntity> page = this.page(
                new Query<SysUserEntity>().getPage(new MapUtils()
                        .put(Constant.PAGE,form.getPage()).put(Constant.LIMIT,form.getLimit())),
                new QueryWrapper<SysUserEntity>()
                        .eq("user_status", 0)
                        .ne("user_id",Constant.DEVELOPER_USERID)
                        .like(StringUtils.isNotBlank(form.getUserName()),"user_name", form.getUserName())
                        .like(StringUtils.isNotBlank(form.getUserPhone()),"user_phone", form.getUserPhone())
                        .apply(form.getSqlFilter() != null, form.getSqlFilter())
                        .orderByAsc("user_id")
        );

        //角色名称 && 盐置为空  手机号码解密 脱敏
        for (SysUserEntity user : page.getRecords()) {
            String roleNames = sysRoleDao.getRoleNamesByUserId(user.getUserId());
            user.setRoleNames(roleNames);
            user.setUserPassword(null);
            user.setUserSalt(null);

            String userPhone = user.getUserPhone();
            if (StringUtils.isNotBlank(userPhone)) {
                userPhone = AESUtil.decrypt(userPhone, Constant.KEY);
                userPhone = DesensitizeUtils.phone(userPhone);
                user.setUserPhone(userPhone);
            }

            String userRealname = user.getUserRealname();
            if (StringUtils.isNotBlank(userRealname))
                user.setUserRealname(DesensitizeUtils.name(userRealname));

        }

        return new PageUtils(page);
    }

    /**
     * 查询用户信息-下拉查询
     * @param form
     * @return
     */
    @Override
    @DataFilter(userFilter = true)
    public List<SysUserEntity> queryUser(SysUserForm form) {
        List<SysUserEntity> list = this.list(
                new QueryWrapper<SysUserEntity>()
                        .eq("user_status", 0)
                        .ne("user_id",Constant.DEVELOPER_USERID)
                        .like(StringUtils.isNotBlank(form.getUserName()),"user_name", form.getUserName())
                        .like(StringUtils.isNotBlank(form.getUserPhone()),"user_phone", form.getUserPhone())
                        .apply(form.getSqlFilter() != null, form.getSqlFilter())
                        .orderByAsc("user_id")
        );

        //盐置为空  手机号码解密 脱敏
        for (SysUserEntity user : list) {
            user.setUserPassword(null);
            user.setUserSalt(null);

            String userPhone = user.getUserPhone();
            if (StringUtils.isNotBlank(userPhone)) {
                userPhone = AESUtil.decrypt(userPhone, Constant.KEY);
                userPhone = DesensitizeUtils.phone(userPhone);
                user.setUserPhone(userPhone);
            }

            String userRealname = user.getUserRealname();
            if (StringUtils.isNotBlank(userRealname))
                user.setUserRealname(DesensitizeUtils.name(userRealname));
        }

        return list;
    }

    /**
     * 根据ID 获取用户信息（包括角色信息）
     * @param userId
     * @return
     */
    @Override
    public SysUserEntity getInfo(Long userId) {
        //1.查询用户信息
        SysUserEntity user = this.getById(userId);

        if (user == null || user.getUserId() == null || !user.valid()) {
            throw new RRException("该用户信息不存在或已删除");
        }

        //盐置为空
        user.setUserPassword(null);
        user.setUserSalt(null);
        //手机号码解密 脱敏
        String userPhone = user.getUserPhone();
        if (StringUtils.isNotBlank(userPhone)) {
            userPhone = AESUtil.decrypt(userPhone, Constant.KEY);
            userPhone = DesensitizeUtils.phone(userPhone);
            user.setUserPhone(userPhone);
        }
        //真实姓名 脱敏
        String userRealname = user.getUserRealname();
        if (StringUtils.isNotBlank(userRealname))
            user.setUserRealname(DesensitizeUtils.name(userRealname));

        //2.查询用户所拥有的角色
        List<SysRoleEntity> roleList = sysRoleDao.queryRoleByUserId(userId);
        user.setRoleList(roleList);

        //3.用户的角色类型-最高权限角色id
        Long roleType = sysRoleDao.getRoleTypeByUserId(userId);
        user.setRoleType(roleType);

        //4.创建人
        SysUserEntity creator = sysUserDao.selectById(user.getUserCreator());
        if (creator != null && creator.getUserId() != Constant.DEVELOPER_USERID) {
            user.setCreatorName(creator.getUserName());
        }

        return user;
    }

    /**
     * 保存用户 && 用户角色权限
     * @param userVO
     * @param creator 操作员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(SysUserVO userVO,SysUserEntity creator) {
        //1.校验参数
        ValidatorUtils.validateEntity(userVO,AddGroup.class);

        //2.校验手机号
        String userPhone = userVO.getUserPhone();
        if (StringUtils.isNotBlank(userPhone)) {
            SysUserEntity other = sysUserDao.selectOne(
                    new QueryWrapper<SysUserEntity>()
                            .eq("user_status", 0)
                            .eq("user_phone", AESUtil.encrypt(userPhone, Constant.KEY))
            );
            if (other != null)
                throw new RRException("该用户手机号已经存在");
        }

        //2.创建用户实体类 密码加密 手机号码加密
        SysUserEntity user = new SysUserEntity(userVO,1,creator.getUserId());

        //2.检查角色是否越权
        checkRole(user,creator);

        //3.保存用户
        this.save(user);

        //4.保存用户与角色关系
        sysUserRoleService.saveOrUpdate(user.getUserId(), user.getRoleIdList());

    }

    /**
     * 修改用户 && 用户角色权限
     * @param userVO
     * @param creator 操作员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(SysUserVO userVO,SysUserEntity creator) {
        //1.校验参数
        ValidatorUtils.validateEntity(userVO,UpdateGroup.class);

        //2.校验 id 是否存在
        SysUserEntity old = this.getById(userVO.getUserId());
        if (old == null || old.getUserId() == null || !old.valid()) {
            throw new RRException("该用户信息不存在或已删除");
        }

        //2.校验手机号
        String userPhone = userVO.getUserPhone();
        if (StringUtils.isNotBlank(userPhone) && userPhone.indexOf("*") == -1) {
            SysUserEntity other = sysUserDao.selectOne(
                    new QueryWrapper<SysUserEntity>()
                            .eq("user_status", 0)
                            .ne("user_id", userVO.getUserId())
                            .eq("user_phone", AESUtil.encrypt(userPhone, Constant.KEY))
            );
            if (other != null)
                throw new RRException("该用户手机号已经存在");
        }

        //2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!creator.devOrSysAdmin() && (old.getUserCreator().compareTo(creator.getUserId()) != 0))
            throw new RRException("没有权限");

        //2023年2月25日 不能修改、删除自己的账号
        if (userVO.getUserId().compareTo(creator.getUserId()) == 0)
            throw new RRException("没有权限!");

        //2.创建用户实体类 不能修改用户类型，不能修改秘密啊
        SysUserEntity user = new SysUserEntity(userVO,2,null);

        //3.检查角色是否越权
        checkRole(user,creator);

        //4.修改用户
        this.updateById(user);

        //5.修改用户与角色关系
        sysUserRoleService.saveOrUpdate(user.getUserId(), user.getRoleIdList());

        //6.变更手机号码-清空token
        String oldUserPhone = old.getUserPhone() == null ? "" : old.getUserPhone();
        String newUserPhone = userVO.getUserPhone() == null ? "" : userVO.getUserPhone();
        if (!oldUserPhone.equals(newUserPhone))
            sysUserTokenService.removeById(userVO.getUserId());

    }

    /**
     * 删除用户（逻辑）
     * @param userId
     * @param creator
     */
    @Override
    public void deleteUser(Long userId,SysUserEntity creator) {
        //1.校验参数
        Assert.isNull(userId,"用户ID不能为空");

        //2.校验 id 是否存在
        SysUserEntity old = this.getById(userId);
        if (old == null || old.getUserId() == null || !old.valid()) {
            throw new RRException("该用户信息不存在或已删除");
        }

        //2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!creator.devOrSysAdmin() && (old.getUserCreator().compareTo(creator.getUserId()) != 0))
            throw new RRException("没有权限");

        //2023年2月25日 不能修改、删除自己的账号
        if (userId.compareTo(creator.getUserId()) == 0)
            throw new RRException("没有权限!");

        //4.逻辑删除用户  status = id
        this.updateById(new SysUserEntity(userId));

        //5.物理删除用户角色
        sysUserRoleDao.deleteByMap(new MapUtils().put("user_id",userId));

        //6.逻辑删除其他相关信息 TODO

        //6.清空token
        sysUserTokenService.removeById(userId);

    }

    /**
     * 打开或关闭用户
     * @param user
     */
    @Override
    public void openOrClose(SysUserEntity user, SysUserEntity creator) {
        //1.校验参数
        Assert.isNull(user.getUserId(),"用户ID不能为空");

        //2.校验 id 是否存在
        SysUserEntity old = this.getById(user.getUserId());
        if (old == null || old.getUserId() == null || !old.valid()) {
            throw new RRException("该用户信息不存在或已删除");
        }

        //2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!creator.devOrSysAdmin() && (old.getUserCreator().compareTo(creator.getUserId()) != 0))
            throw new RRException("没有权限");

        //2023年2月25日 不能修改、删除自己的账号
        if (user.getUserId().compareTo(creator.getUserId()) == 0)
            throw new RRException("没有权限!");

        //3.开放或关闭
        this.updateById(user);

        //4.关闭用户-清除token
        if (user.getUserRecycle() == 1)
            sysUserTokenService.removeById(user.getUserId());


        //4.关闭用户
        if (!user.open()) {
            //其他相关信息 TODO
        }
    }

    /**
     * 重置密码
     * @param passwordVO
     */
    @Override
    public void reset(SysPasswordVO passwordVO, SysUserEntity creator) {
        //1.校验参数
        ValidatorUtils.validateEntity(passwordVO);

        //2.校验 id 是否存在
        SysUserEntity old = this.getById(passwordVO.getUserId());
        if (old == null || old.getUserId() == null || !old.valid()) {
            throw new RRException("该用户信息不存在或已删除");
        }

        //2021年11月17日 其他管理员开放角色和用户权限（只能自己创建的）
        if (!creator.devOrSysAdmin() && (old.getUserCreator().compareTo(creator.getUserId()) != 0))
            throw new RRException("没有权限");

        //3.生成实体类
        SysUserEntity user = new SysUserEntity(passwordVO);

        //4.重置密码
        this.updateById(user);

        //5.清空token
        sysUserTokenService.removeById(passwordVO.getUserId());

    }

    /**
     * 修改密码
     * @param loginUser       登录用户
     * @param form     原密码 和 新密码
     */
    @Override
    public void updatePassword(SysUserEntity loginUser, PasswordForm form) {
        //参数校验
        Assert.isBlank(form.getPassword(), "旧密码不为能空");
        Assert.isBlank(form.getNewPassword(), "新密码不为能空");

        //查询库中最新密码数据（登录态快照可能滞后于渐进升级），BCrypt 无法在 SQL 中比对，需内存验证
        SysUserEntity dbUser = this.getById(loginUser.getUserId());
        if (dbUser == null || !PasswordCodec.matches(form.getPassword(), dbUser.getUserPassword(), dbUser.getUserSalt())) {
            throw new RRException("原密码不正确");
        }

        SysUserEntity userEntity = new SysUserEntity();
        userEntity.setUserId(loginUser.getUserId());
        userEntity.setUserPassword(PasswordCodec.encode(form.getNewPassword()));
        this.updateById(userEntity);
    }

    /**
     * 检查角色是否越权
     * @param user 实体
     * @param creator 操作人
     */
    private void checkRole(SysUserEntity user,SysUserEntity creator){
        if(user.getRoleIdList() == null || user.getRoleIdList().size() == 0){
            return;
        }

        //如果不是开发员或系统管理员，则需要判断用户的角色是否超过自己拥有的角色
        if(creator.devOrSysAdmin()){
            return ;
        }

        //查询用户拥有的角色列表
        List<Long> roleIdList = sysRoleDao.queryRoleIdByUserId(creator.getUserId());

        //查询用户自己创建的角色列表
        List<Long> roleIdList2 = sysRoleDao.queryRoleIdByCreator(creator.getUserId());
        roleIdList.addAll(roleIdList2);

        //判断是否越权
        if(!roleIdList.containsAll(user.getRoleIdList())){
            throw new RRException("新增用户所选角色，超出本人权限");
        }
    }
}