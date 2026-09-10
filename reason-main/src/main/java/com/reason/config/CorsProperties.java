package com.reason.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域白名单配置（T16 配置安全：从通配 "*" 收敛为显式白名单）
 *
 * <p>默认空列表 = 仅同源；需跨域接入的前端源在此逐条登记（精确匹配完整 Origin，
 * 如 http://localhost:9527）。未登记的源在预检/实际请求与 401 响应中均不会得到
 * Access-Control-Allow-* 头（早期实现为 allowedOriginPatterns("*") + allowCredentials(true)，
 * 等价"任意站点可带凭据请求"，属安全债）。</p>
 */
@Component
@ConfigurationProperties(prefix = "reason.cors")
public class CorsProperties {

    /** 允许的跨域源（精确匹配：scheme://host[:port]） */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * 判断 Origin 是否在白名单内（null/空白一律 false——无 Origin 的非浏览器请求不受 CORS 约束）
     */
    public boolean isAllowed(String origin) {
        return origin != null && !origin.isBlank() && allowedOrigins.contains(origin);
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
