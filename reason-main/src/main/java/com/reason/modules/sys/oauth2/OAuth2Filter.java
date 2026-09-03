package com.reason.modules.sys.oauth2;

import com.reason.common.utils.JsonUtil;
import com.reason.common.utils.HttpContextUtils;
import com.reason.common.utils.Result;
import com.reason.common.utils.StringUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.subject.Subject;

import java.io.IOException;

/**
 * oauth2 认证过滤器（基于 jakarta.servlet.Filter 自研，替换 shiro-web 的 javax 过滤器）
 * 认证流程：白名单/预检放行 → 提取 token → subject.login(OAuth2Token) → 放行或返回 401
 * 无状态会话：请求结束后清理 ThreadContext，防止 Tomcat 线程复用导致串号
 *
 */
@Slf4j
public class OAuth2Filter implements Filter {

    private static final String OPTIONS = "OPTIONS";

    /**
     * 免认证白名单（对应原 ShiroFilterFactoryBean filterMap 的 anon 路径，前缀匹配）
     * 注意：server.servlet.context-path=/api，uri 以 /api 开头，白名单同时兼容两套前缀
     */
    private static final String[] ANON_PATHS = {
        "/druid", "/webjars", "/swagger-resources", "/v2/api-docs", "/v2/api-docs-ext",
        "/v3/api-docs", "/doc.html", "/swagger-ui", "/sys/init", "/sys/code",
        "/sys/login", "/sys/auth", "/test", "/captcha.jpg", "/aaa.txt", "/app", "/etc/test"
    };

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        //1.跨域预检请求直接放行
        if (OPTIONS.equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        //2.白名单路径放行
        String uri = request.getRequestURI();
        if (isAnonPath(uri)) {
            chain.doFilter(request, response);
            return;
        }

        //3.获取请求token，不存在直接返回401
        String token = getRequestToken(request);
        if (StringUtils.isBlank(token)) {
            writeJson(response, HttpStatus.SC_UNAUTHORIZED, "无效 token");
            return;
        }

        Subject subject = SecurityUtils.getSubject();
        try {
            //4.认证（OAuth2Realm 校验 token 有效性并加载用户）
            subject.login(new OAuth2Token(token));
            chain.doFilter(request, response);
        } catch (AuthenticationException e) {
            log.warn("接口认证失败，uri：{}，原因：{}", uri, e.getMessage());
            Throwable cause = e.getCause() == null ? e : e.getCause();
            writeJson(response, HttpStatus.SC_UNAUTHORIZED, cause.getMessage());
        } finally {
            //5.清理线程上下文，防止线程复用串号
            ThreadContext.remove();
        }
    }

    /**
     * 判断是否免认证路径
     */
    private boolean isAnonPath(String uri) {
        for (String p : ANON_PATHS) {
            if (uri.startsWith(p) || uri.startsWith("/api" + p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取请求的token（header 优先，其次请求参数）
     */
    private String getRequestToken(HttpServletRequest httpRequest) {
        //从header中获取token
        String token = httpRequest.getHeader("token");
        //如果header中不存在token，则从参数中获取token
        if (StringUtils.isBlank(token)) {
            token = httpRequest.getParameter("token");
        }
        return token;
    }

    /**
     * 输出 JSON 响应
     */
    private void writeJson(HttpServletResponse response, Integer code, String message) {
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