package com.reason.modules.parking.security;

import com.reason.common.utils.JsonUtil;
import com.reason.common.utils.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 设备通道鉴权过滤器（/device/** 专用，其余路径透传）
 *
 * <p>设备上报走独立通道，不占系统用户 token 体系：请求头携带
 * {@code X-Device-Token}，与 yml {@code reason.device.access-token} 比对。
 * M0 单密钥最小鉴权（密钥泄露只能以设备身份上报业务事件，无法进入管理端）；
 * 设备注册台账/密钥轮换/双向 TLS 属 M2 设备治理演进。</p>
 *
 * <p>安全默认：未配置 access-token 时拒绝一切设备请求（宁可不服务，不可裸奔）。</p>
 */
@Slf4j
@Component
public class DeviceAuthFilter extends OncePerRequestFilter {

    private static final String DEVICE_PATH_PREFIX = "/device/";
    private static final String DEVICE_TOKEN_HEADER = "X-Device-Token";

    @Value("${reason.device.access-token:}")
    private String accessToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = trimContextPath(request);
        //非设备通道：透传
        if (!uri.startsWith(DEVICE_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        //设备通道：校验令牌（未配置 = 拒绝）
        String token = request.getHeader(DEVICE_TOKEN_HEADER);
        if (StringUtils.hasText(accessToken) && accessToken.equals(token)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("设备通道鉴权失败，uri：{}，token 缺失或不匹配", request.getRequestURI());
        writeJson(request, response);
    }

    /** 去掉 context-path（/api）取实际路径 */
    private String trimContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (StringUtils.hasText(ctx) && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }

    private void writeJson(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        //CORS 回显请求来源（不依赖 RequestContextHolder，便于纯单测与过滤器直测）
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }
        try {
            response.getWriter().print(JsonUtil.toJsonString(Result.error(401, "无效的设备令牌")));
        } catch (IOException e) {
            log.error("设备鉴权失败响应输出异常", e);
        }
    }
}
