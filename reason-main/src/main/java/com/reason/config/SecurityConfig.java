package com.reason.config;

import com.reason.common.utils.JsonUtil;
import com.reason.common.utils.Result;
import com.reason.modules.sys.security.AuthTokenFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Spring Security 配置（Spring Boot 3 / Security 6 版，替代原 ShiroConfig）
 *
 * <p>认证：自有 token 体系（sys_user_token 表），由 AuthTokenFilter 完成认证并填充 SecurityContext；
 * 授权：@EnableMethodSecurity 开启方法级鉴权，Controller 使用 @PreAuthorize("hasAuthority('xxx')")；
 * 会话：前后端分离无状态（STATELESS），不依赖 HttpSession。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 免认证白名单（T16 收敛：逐条核对 Controller/静态资源存活后重列；renren 骨架幽灵路径已删——
     * /druid/**（stat-view-servlet 未启用）、/swagger-resources/**、/v2/api-docs*（springfox 遗产，现为 knife4j/openapi3）、
     * /sys/code、/sys/auth、/test/**、/aaa.txt、/app/**、/etc/test/**。Security 按 context-path 内的路径匹配）
     */
    private static final String[] ANON_PATHS = {
            //接口文档（knife4j/openapi3 静态资源；prod 由 knife4j.production=true 屏蔽内容）
            "/webjars/**", "/doc.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            //登录前必需：初始配置 + 登录
            "/sys/init", "/sys/login",
            //设备事件/心跳通道：免管理端 token（设备无登录态）；安全由 per-device HMAC 承担（DeviceChannelAuthenticator）
            "/device/event", "/device/heartbeat",
            //运维探活
            "/actuator/health", "/actuator/info",
            //图片验证码（SysLoginController 保留接口）
            "/captcha.jpg",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthTokenFilter authTokenFilter,
                                                   CorsProperties corsProperties) throws Exception {
        http
                //前后端分离 + 自有 token 体系：关闭 csrf，会话无状态
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        //跨域预检与异常/转发请求放行（/error 不放行会导致异常响应被拦截形成循环）
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(ANON_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint(corsProperties)))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 认证失败统一输出 401 JSON（与原 OAuth2Filter 响应格式一致）
     *
     * <p>T16 CORS 收敛：Access-Control-Allow-Origin 仅回显白名单内的 Origin
     * （原实现无条件回显请求 Origin——任意站点可跨域读取 401 响应）。</p>
     */
    private AuthenticationEntryPoint restAuthenticationEntryPoint(CorsProperties corsProperties) {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=utf-8");
            String origin = request.getHeader("Origin");
            if (corsProperties.isAllowed(origin)) {
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Origin", origin);
            }
            try {
                response.getWriter().print(JsonUtil.toJsonString(Result.error(401, "无效 token")));
            } catch (IOException e) {
                throw e;
            }
        };
    }
}
