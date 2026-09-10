/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（认证体系为 STATELESS token，无需 HttpSession——@EnableRedisHttpSession 已移除）
 *
 * <p>T16 配置安全：原 allowedOriginPatterns("*") + allowCredentials(true) 等价
 * "任意站点可带凭据跨域请求"，已收敛为显式白名单（reason.cors.allowed-origins，
 * 默认空 = 不与任何 Origin 匹配 => 全部跨域请求被拒，含预检）。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 空白名单同样注册映射：无匹配 Origin => 拒绝所有跨域（保持行为显式可预期）。
        // 注意：必须用 allowedOrigins 覆盖——CorsRegistration 构造器默认预置 allowedOrigins=["*"]，
        // 仅设置 allowedOriginPatterns 不会清除它，allowCredentials=true 时遇到跨域请求
        // 会触发 Spring 校验异常 "allowedOrigins cannot contain \"*\""（批次5 实测暴露）
        registry.addMapping("/**")
            .allowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
            .allowCredentials(true)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .maxAge(3600);
    }
}