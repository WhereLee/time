package com.reason.modules.sys.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.reason.common.utils.PasswordCodec;
import com.reason.modules.sys.dao.SysRoleDao;
import com.reason.modules.sys.dao.SysUserDao;
import com.reason.modules.sys.entity.SysDicIplistEntity;
import com.reason.modules.sys.entity.SysLoginEntity;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.form.SysLoginForm;
import com.reason.modules.sys.service.SysDictionaryService;
import com.reason.modules.sys.service.SysUserTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录核心链路单测（关键分支）
 *
 * <p>取舍说明：login 内聚的「尝试次数锁定」逻辑抽取为 LoginAttemptGuard 的重构
 * 已登记 document/roadmap/login-attempt-guard-extraction.md，触发时执行；
 * 本类只测关键分支，mock 量偏大是已知取舍。</p>
 */
@DisplayName("登录核心链路")
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplLoginTest {

    private static final String LOGINNAME = "adminManager";
    private static final String PASSWORD = "admin123";
    private static final String LEGACY_SALT = "CYiKIzx4410U9yaBPBHE";
    private static final String LEGACY_HASH = "06bf8058a83e7c94b345e6eab9964956ea13ce904e7b5025e333127c24f94794";
    private static final String REDIS_KEY = "user_" + LOGINNAME;

    @Mock
    private com.reason.common.utils.ParamUtils paramUtils;
    @Mock
    private com.reason.common.utils.RedisUtils redisUtils;
    @Mock
    private SysUserDao sysUserDao;
    @Mock
    private SysRoleDao sysRoleDao;
    @Mock
    private SysUserTokenService sysUserTokenService;
    @Mock
    private SysDictionaryService sysDictionaryService;

    @Spy
    @InjectMocks
    private SysUserServiceImpl service;

    @Captor
    private ArgumentCaptor<com.reason.modules.sys.entity.SysUserEntity> userCaptor;

    private SysLoginForm loginForm() {
        SysLoginForm form = new SysLoginForm();
        form.setLoginname(LOGINNAME);
        form.setPassword(PASSWORD);
        return form;
    }

    private SysUserEntity userMock(String storedHash, String salt) {
        SysUserEntity u = org.mockito.Mockito.mock(SysUserEntity.class);
        when(u.getUserId()).thenReturn(2L);
        when(u.getUserPassword()).thenReturn(storedHash);
        when(u.getUserSalt()).thenReturn(salt);
        when(u.open()).thenReturn(true);
        //字段是 primitive long，mock 默认返回 0 而非 null，必须显式 stub 为「刚变更过」
        when(u.getUserPwdChangetime()).thenReturn(System.currentTimeMillis() / 1000);
        return u;
    }

    /** 错误路径专用：密码验证在 open()/getUserId() 消费点之前抛出，只 stub 被消费的方法 */
    private SysUserEntity errorPathUserMock() {
        SysUserEntity u = org.mockito.Mockito.mock(SysUserEntity.class);
        when(u.getUserPassword()).thenReturn("whatever-hash");
        when(u.getUserSalt()).thenReturn("any");
        return u;
    }

    private void stubCommon() {
        //IP 黑白名单均为空：黑名单跳过、白名单放行所有 IP
        when(sysDictionaryService.getIpList()).thenReturn(new SysDicIplistEntity());
        //getChangeForce 依赖：口令变更时限 30 天、提醒模式
        when(paramUtils.getChangeForceAndLimit())
                .thenReturn(Map.of("changeLimit", 30, "changeForce", 2));
        when(sysRoleDao.queryRoleIdByUserId(2L)).thenReturn(List.of(2L));
        when(sysUserTokenService.createToken(2L)).thenReturn(new SysLoginEntity());
        //spy 上的 updateById 必须用 doReturn（否则会执行真实方法打到 mapper）
        doReturn(true).when(service).updateById(any(com.reason.modules.sys.entity.SysUserEntity.class));
    }

    @Test
    @DisplayName("BCrypt 用户登录成功：发放 token，不触发渐进升级")
    void BCrypt用户_登录成功_不触发渐进升级() {
        when(paramUtils.getAttemptLimtAndLockTime())
                .thenReturn(Map.of("attemptLimit", 0, "lockTime", 5));
        SysUserEntity bcryptUser = userMock(PasswordCodec.encode(PASSWORD), "any");
        when(sysUserDao.getUserByLoginname(LOGINNAME)).thenReturn(bcryptUser);
        stubCommon();

        SysLoginEntity result = service.login(loginForm(), "127.0.0.1");

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(2L);
        //仅「更新登录时间」一次 updateById，无重哈希
        verify(service, times(1)).updateById(any(com.reason.modules.sys.entity.SysUserEntity.class));
        verify(redisUtils).deleteKey(REDIS_KEY);
    }

    @Test
    @DisplayName("遗留 SHA-256 用户登录成功：密码被重哈希为 BCrypt（渐进迁移核心行为）")
    void 遗留用户_登录成功_密码升级为BCrypt() {
        when(paramUtils.getAttemptLimtAndLockTime())
                .thenReturn(Map.of("attemptLimit", 0, "lockTime", 5));
        SysUserEntity legacyUser = userMock(LEGACY_HASH, LEGACY_SALT);
        when(sysUserDao.getUserByLoginname(LOGINNAME)).thenReturn(legacyUser);
        stubCommon();

        service.login(loginForm(), "127.0.0.1");

        //两次 updateById：登录时间更新 + 渐进升级；升级实体必须携带 BCrypt 哈希
        verify(service, times(2)).updateById(userCaptor.capture());
        assertThat(userCaptor.getAllValues())
                .anySatisfy(e -> assertThat(e.getUserPassword()).startsWith("$2a$"));
    }

    @Test
    @DisplayName("密码错误达到尝试上限：账号限时锁定并写入 Redis")
    void 密码错误_达到尝试上限_账号锁定() {
        when(paramUtils.getAttemptLimtAndLockTime())
                .thenReturn(Map.of("attemptLimit", 3, "lockTime", 5));
        when(sysDictionaryService.getIpList()).thenReturn(new SysDicIplistEntity());
        JSONObject existing = new JSONObject();
        existing.put("times", 2);
        existing.put("lock", false);
        when(redisUtils.hasKey(REDIS_KEY)).thenReturn(true);
        when(redisUtils.getKeyValue(REDIS_KEY)).thenReturn(existing);
        SysUserEntity errorUser = errorPathUserMock();
        when(sysUserDao.getUserByLoginname(LOGINNAME)).thenReturn(errorUser);

        assertThatThrownBy(() -> service.login(loginForm(), "127.0.0.1"))
                .isInstanceOf(com.reason.common.exception.RRException.class)
                .hasMessageContaining("锁定");

        verify(redisUtils).deleteKey(REDIS_KEY);
        verify(redisUtils).setKeyValue(eq(REDIS_KEY), any(), eq(300L));
    }

    @Test
    @DisplayName("密码错误未达上限：错误次数累加并刷新过期时间")
    void 密码错误_未达上限_次数累加() {
        when(paramUtils.getAttemptLimtAndLockTime())
                .thenReturn(Map.of("attemptLimit", 3, "lockTime", 5));
        when(sysDictionaryService.getIpList()).thenReturn(new SysDicIplistEntity());
        JSONObject existing = new JSONObject();
        existing.put("times", 0);
        existing.put("lock", false);

        when(redisUtils.hasKey(REDIS_KEY)).thenReturn(true);
        when(redisUtils.getKeyValue(REDIS_KEY)).thenReturn(existing);
        SysUserEntity errorUser = errorPathUserMock();
        when(sysUserDao.getUserByLoginname(LOGINNAME)).thenReturn(errorUser);

        assertThatThrownBy(() -> service.login(loginForm(), "127.0.0.1"))
                .isInstanceOf(com.reason.common.exception.RRException.class)
                .hasMessageContaining("账号或密码错误");

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisUtils).setKeyValue(eq(REDIS_KEY), valueCaptor.capture(), any());
        JSONObject saved = (JSONObject) valueCaptor.getValue();
        assertThat(saved.getInteger("times")).isEqualTo(1);
    }
}
