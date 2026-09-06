package com.reason.modules.sys.security;

import com.reason.common.exception.RRException;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.service.AuthService;
import com.reason.modules.sys.service.SysUserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthTokenFilter 单元测试：认证过滤器三态行为 + 上下文清理防串号
 */
@DisplayName("认证过滤器")
@ExtendWith(MockitoExtension.class)
class AuthTokenFilterTest {

    @Mock
    private AuthService authService;
    @Mock
    private SysUserService sysUserService;

    private AuthTokenFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private List<Authentication> capturedAuths;
    private boolean chainInvoked;

    @BeforeEach
    void setUp() {
        filter = new AuthTokenFilter();
        ReflectionTestUtils.setField(filter, "authService", authService);
        ReflectionTestUtils.setField(filter, "sysUserService", sysUserService);
        request = new MockHttpServletRequest("GET", "/api/sys/user/list");
        response = new MockHttpServletResponse();
        //writeJson → HttpContextUtils.getOrigin() 依赖请求上下文（生产由 RequestContextFilter 前置提供）
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        capturedAuths = new ArrayList<>();
        chainInvoked = false;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    /** 业务链路探针：捕获链路执行时刻的 SecurityContext */
    private FilterChain capturingChain() {
        return (req, res) -> {
            chainInvoked = true;
            capturedAuths.add(SecurityContextHolder.getContext().getAuthentication());
        };
    }

    @Test
    @DisplayName("无 token：放行链路且不设置认证上下文（白名单语义由 SecurityConfig 负责）")
    void 无token_放行链路且不设置认证上下文() throws Exception {
        filter.doFilter(request, response, capturingChain());

        assertThat(chainInvoked).isTrue();
        assertThat(capturedAuths).hasSize(1).allSatisfy(a -> assertThat(a).isNull());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("无效 token：返回 401 JSON、不进入业务链路、上下文无残留")
    void 无效token_返回401JSON且不进入业务链路_上下文被清理() throws Exception {
        request.addHeader("token", "bad-token");
        when(authService.verifyTokenAndGetUser(eq("bad-token"), anyString()))
                .thenThrow(new RRException("token失效，请重新登录"));

        filter.doFilter(request, response, capturingChain());

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString()).contains("401").contains("token失效");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authService, never()).queryUserPermissions(any());
    }

    @Test
    @DisplayName("有效 token：构建认证上下文、权限串映射为 authorities、请求结束后上下文被清理")
    void 有效token_构建认证上下文_权限串映射为authorities_结束后上下文被清理() throws Exception {
        request.addHeader("token", "good-token");
        SysUserEntity user = org.mockito.Mockito.mock(SysUserEntity.class);
        when(authService.verifyTokenAndGetUser(eq("good-token"), anyString())).thenReturn(user);
        when(authService.queryUserPermissions(user)).thenReturn(Set.of("sys:user:list", "sys:role:list"));

        filter.doFilter(request, response, capturingChain());

        assertThat(chainInvoked).isTrue();
        assertThat(capturedAuths).hasSize(1);
        Authentication auth = capturedAuths.get(0);
        assertThat(auth.getPrincipal()).isSameAs(user);
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("sys:user:list", "sys:role:list");
        //finally 清理防串号：请求结束后上下文必须为空
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
