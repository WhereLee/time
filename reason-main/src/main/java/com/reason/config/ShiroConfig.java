package com.reason.config;

import com.reason.modules.sys.oauth2.OAuth2Filter;
import com.reason.modules.sys.oauth2.OAuth2Realm;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Shiro配置（Spring Boot 3 / Jakarta 适配版）
 * 说明：shiro-web 的过滤器链基于 javax.servlet 实现，不适用于 Boot3；
 * 认证入口改为自研 jakarta Filter（OAuth2Filter），通过 FilterRegistrationBean 注册；
 * 认证/授权（Realm、@RequiresPermissions 注解）仍由 shiro-core/spring 提供。
 *
 */
@Configuration
public class ShiroConfig {

    @Bean("securityManager")
    public DefaultSecurityManager securityManager(OAuth2Realm oAuth2Realm) {
        DefaultSecurityManager securityManager = new DefaultSecurityManager();
        securityManager.setRealm(oAuth2Realm);
        securityManager.setRememberMeManager(null);
        // 供 SecurityUtils / 注解拦截器获取
        SecurityUtils.setSecurityManager(securityManager);
        return securityManager;
    }

    /**
     * 注册 OAuth2 认证过滤器（自研 jakarta Filter）
     */
    @Bean
    public FilterRegistrationBean<OAuth2Filter> oauth2FilterRegistration() {
        FilterRegistrationBean<OAuth2Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OAuth2Filter());
        registration.addUrlPatterns("/*");
        registration.setName("oauth2Filter");
        // 在 RequestContextFilter 之后执行（顺序：RequestContextFilter → OAuth2Filter → 业务）
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean("lifecycleBeanPostProcessor")
    public LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }

    /**
     * 启用 @RequiresPermissions 等注解鉴权
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(DefaultSecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

}