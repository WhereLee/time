package com.reason.modules.sys.service.impl;

import com.reason.common.exception.RRException;
import com.reason.modules.sys.dao.SysMenuDao;
import com.reason.modules.sys.dao.SysRoleDao;
import com.reason.modules.sys.dao.SysUserDao;
import com.reason.modules.sys.dao.SysUserTokenDao;
import com.reason.modules.sys.entity.SysMenuEntity;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.entity.SysUserTokenEntity;
import com.reason.modules.sys.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试：token 校验全分支 + 权限查询
 * （其中「token 有效但用户已删除」用例对应 A1 修复的 NPE 缺陷）
 */
@DisplayName("认证服务")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysMenuDao sysMenuDao;
    @Mock
    private SysUserDao sysUserDao;
    @Mock
    private SysUserTokenDao sysUserTokenDao;
    @Mock
    private SysRoleDao sysRoleDao;
    @Mock
    private SysUserService sysUserService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        //@Value 注入的豁免 URI 集合（yml: reason.security.change-password-exempt-uris），单测需手动填充
        ReflectionTestUtils.setField(authService, "changePasswordExemptUris",
                List.of("/api/sys/menu/nav", "/api/sys/user/info", "/api/sys/logout", "/api/sys/user/password"));
    }

    private SysUserTokenEntity validTokenEntity() {
        SysUserTokenEntity te = mock(SysUserTokenEntity.class);
        when(te.getExpiretime()).thenReturn(System.currentTimeMillis() / 1000 + 600);
        when(te.getUserId()).thenReturn(2L);
        return te;
    }

    private SysUserEntity userMock(boolean open) {
        SysUserEntity u = mock(SysUserEntity.class);
        when(u.open()).thenReturn(open);
        return u;
    }

    @Test
    @DisplayName("token 不存在：抛出 token 失效异常")
    void token不存在_抛token失效异常() {
        when(sysUserTokenDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.verifyTokenAndGetUser("t", "/api/sys/x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("token失效");
    }

    @Test
    @DisplayName("token 已过期：抛出 token 失效异常")
    void token过期_抛token失效异常() {
        SysUserTokenEntity te = mock(SysUserTokenEntity.class);
        when(sysUserTokenDao.selectOne(any())).thenReturn(te);
        when(te.getExpiretime()).thenReturn(System.currentTimeMillis() / 1000 - 1);

        assertThatThrownBy(() -> authService.verifyTokenAndGetUser("t", "/api/sys/x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("token失效");
    }

    @Test
    @DisplayName("token 有效但用户已删除：抛出账号不存在，不产生 NPE（A1 修复的缺陷）")
    void token有效但用户已删除_抛账号不存在_不产生NPE() {
        SysUserTokenEntity te = validTokenEntity();
        when(sysUserTokenDao.selectOne(any())).thenReturn(te);
        when(sysUserDao.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.verifyTokenAndGetUser("t", "/api/sys/x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("账号不存在");
    }

    @Test
    @DisplayName("账号被锁定：抛出锁定异常")
    void 账号被锁定_抛锁定异常() {
        SysUserTokenEntity te = validTokenEntity();
        when(sysUserTokenDao.selectOne(any())).thenReturn(te);
        SysUserEntity lockedUser = userMock(false);
        when(sysUserDao.selectById(any())).thenReturn(lockedUser);

        assertThatThrownBy(() -> authService.verifyTokenAndGetUser("t", "/api/sys/x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("锁定");
    }

    @Test
    @DisplayName("强改密码：force=3 且非豁免 URI 时抛出 code=888")
    void 强改密码_非豁免URI_抛888() {
        SysUserTokenEntity te = validTokenEntity();
        when(sysUserTokenDao.selectOne(any())).thenReturn(te);
        SysUserEntity user = userMock(true);
        when(sysUserDao.selectById(any())).thenReturn(user);
        when(sysUserService.getChangeForce(user)).thenReturn(3);

        RRException e = catchThrowableOfType(
                () -> authService.verifyTokenAndGetUser("t", "/api/sys/other"), RRException.class);

        assertThat(e).isNotNull();
        assertThat(e.getCode()).isEqualTo(888);
        assertThat(e.getMessage()).contains("请先变更密码");
    }

    @Test
    @DisplayName("强改密码：命中豁免 URI 时不执行强改检查")
    void 强改密码_豁免URI_不执行检查() {
        SysUserTokenEntity te = validTokenEntity();
        when(sysUserTokenDao.selectOne(any())).thenReturn(te);
        SysUserEntity user = userMock(true);
        when(sysUserDao.selectById(any())).thenReturn(user);
        when(sysRoleDao.getRoleTypeByUserId(any())).thenReturn(2L);

        SysUserEntity result = authService.verifyTokenAndGetUser("t", "/api/sys/menu/nav");

        verify(sysUserService, never()).getChangeForce(any());
        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("正常路径：roleType 被填充并返回用户")
    void 正常路径_填充roleType并返回用户() {
        SysUserTokenEntity te = validTokenEntity();
        when(sysUserTokenDao.selectOne(any())).thenReturn(te);
        SysUserEntity user = userMock(true);
        when(sysUserDao.selectById(any())).thenReturn(user);
        when(sysRoleDao.getRoleTypeByUserId(any())).thenReturn(2L);

        SysUserEntity result = authService.verifyTokenAndGetUser("t", "/api/sys/x");

        verify(user).setRoleType(2L);
        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("权限查询：开发员取全量菜单权限")
    void 开发员_权限取全量菜单() {
        SysUserEntity dev = mock(SysUserEntity.class);
        when(dev.developer()).thenReturn(true);
        SysMenuEntity m1 = mock(SysMenuEntity.class);
        when(m1.getMenuPerms()).thenReturn("a");
        SysMenuEntity m2 = mock(SysMenuEntity.class);
        when(m2.getMenuPerms()).thenReturn("b");
        when(sysMenuDao.selectByMap(any())).thenReturn(List.of(m1, m2));

        Set<String> perms = authService.queryUserPermissions(dev);

        assertThat(perms).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("权限查询：普通用户按逗号拆分权限串并过滤空值")
    void 普通用户_权限串按逗号拆分并过滤空值() {
        SysUserEntity user = mock(SysUserEntity.class);
        when(user.developer()).thenReturn(false);
        when(user.getUserId()).thenReturn(2L);
        when(sysMenuDao.queryPermsByUserId(2L)).thenReturn(List.of("a,b", "c", " "));

        Set<String> perms = authService.queryUserPermissions(user);

        assertThat(perms).containsExactlyInAnyOrder("a", "b", "c");
    }
}
