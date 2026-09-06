package com.reason.modules.sys.security;

import com.reason.common.exception.RRException;
import com.reason.common.utils.HttpContextUtils;
import com.reason.common.utils.JsonUtil;
import com.reason.common.utils.Result;
import com.reason.common.utils.StringUtils;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.service.AuthService;
import com.reason.modules.sys.service.SysUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证过滤器（Spring Security 版，替代原 Shiro OAuth2Filter + OAuth2Realm 认证链路）
 *
 * <p>流程：提取 token → 验证（token 表 / 账号状态 / 强改密码）→ 构建 SecurityContext
 * （principal = 用户实体，authorities = 权限串集合）。</p>
 *
 * <p>无 token 的请求不设置认证信息，由 SecurityConfig 的 authorizeHttpRequests 决定
 * 放行白名单或输出 401；验证失败抛出 RRException 由本过滤器统一转为 401 JSON。</p>
 *
 * <p>无状态会话：请求结束后清理 SecurityContextHolder，防止 Tomcat 线程复用导致串号。</p>
 */
@Slf4j
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "token";

    @Autowired
    private AuthService authService;
    @Autowired
    @Lazy
    private SysUserService sysUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.isNotBlank(token)) {
                //认证：token 有效性 / 账号状态 / 口令强制变更检查（失败抛 RRException）
                SysUserEntity user = authService.verifyTokenAndGetUser(token, request.getRequestURI());
                //授权：加载用户权限串集合（TODO 迭代项：加 Redis 缓存，避免每请求查库）
                Set<String> perms = authService.queryUserPermissions(user);
                var authorities = perms.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
                Authentication authentication =
                        new UsernamePasswordAuthenticationToken(user, token, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            chain.doFilter(request, response);
        } catch (RRException e) {
            SecurityContextHolder.clearContext();
            log.warn("接口认证失败，uri：{}，原因：{}", request.getRequestURI(), e.getMessage());
            writeJson(response, 401, e.getMessage());
        } finally {
            //清理线程上下文，防止线程复用串号
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 获取请求的token（header 优先，其次请求参数）
     */
    private String extractToken(HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader(TOKEN_HEADER);
        if (StringUtils.isBlank(token)) {
            token = httpRequest.getParameter(TOKEN_HEADER);
        }
        return token;
    }

    /**
     * 输出 JSON 响应（保持原 OAuth2Filter 响应格式）
     */
    private void writeJson(HttpServletResponse response, int code, String message) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", HttpContextUtils.getOrigin());
        try {
            String json = JsonUtil.toJsonString(Result.error(code, message));
            response.getWriter().print(json);
        } catch (IOException e) {
            log.error("认证失败响应输出异常", e);
        }
    }
}
