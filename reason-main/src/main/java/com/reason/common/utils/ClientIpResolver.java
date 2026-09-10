package com.reason.common.utils;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 客户端 IP 解析（T19 认证前置）
 *
 * <p>X-Forwarded-For 的语义是"代理链追加"，只有请求确实来自代理时才有可信度。
 * 原实现（IPUtils.getIpAddr）无条件信任 XFF/Proxy-Client-IP 等一系列代理头、
 * 且直接返回整串——任意客户端可伪造头冒充任意 IP，登录黑白名单/限速/审计全部失守。</p>
 *
 * <p>本实现规则（fail-secure）：
 * <ol>
 *   <li>remoteAddr 不在可信代理清单 → 一律返回 remoteAddr，忽略一切代理头；</li>
 *   <li>在清单内 → 解析 X-Forwarded-For，从右往左取第一个"非可信代理"地址
 *       （最右侧 = 最近一跳代理写入的真实客户端；更左侧可能是客户端伪造的填充值）；</li>
 *   <li>XFF 缺失 / 全为可信代理 / 全为 unknown → 退回 remoteAddr。</li>
 * </ol></p>
 *
 * <p>可信代理默认空（本地直连 / 无代理部署 = 不解析任何代理头）；有 nginx 等前置代理时
 * 在 reason.security.trusted-proxies 逐条登记（如 127.0.0.1）。</p>
 */
@Component
public class ClientIpResolver {

    @Value("${reason.security.trusted-proxies:}")
    private String trustedProxiesRaw;

    private Set<String> trustedProxies = Collections.emptySet();

    @PostConstruct
    void initTrustedProxies() {
        if (trustedProxiesRaw == null || trustedProxiesRaw.isBlank()) {
            trustedProxies = Collections.emptySet();
            return;
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(parsed::add);
        trustedProxies = parsed;
    }

    /**
     * 解析客户端 IP（用于登录防护/审计展示等联系信息，不作鉴权唯一凭据）
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !trustedProxies.contains(remoteAddr)) {
            // 非可信代理：代理头可被任意伪造，直接忽略
            return remoteAddr;
        }
        String xff = request.getHeader("x-forwarded-for");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }
        String[] parts = xff.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (candidate.isEmpty() || "unknown".equalsIgnoreCase(candidate)) {
                continue;
            }
            if (!trustedProxies.contains(candidate)) {
                return candidate;
            }
        }
        // 链上全为可信代理（异常形态）：退回直连地址
        return remoteAddr;
    }
}
