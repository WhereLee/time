package com.reason.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客户端 IP 解析单测（T19：可信代理白名单 + XFF 末跳；防伪造）
 */
@DisplayName("客户端 IP 解析（T19）")
class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
        // 默认场景：可信代理 = 127.0.0.1（模拟同机 nginx/Jasypt 前置形态）
        ReflectionTestUtils.setField(resolver, "trustedProxiesRaw", "127.0.0.1");
        resolver.initTrustedProxies();
    }

    private HttpServletRequest request(String remoteAddr, String xff) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getRemoteAddr()).thenReturn(remoteAddr);
        when(r.getHeader("x-forwarded-for")).thenReturn(xff);
        return r;
    }

    @Test
    @DisplayName("非可信来源直连：忽略一切代理头，返回 remoteAddr（伪造 XFF 无效）")
    void 非可信直连_忽略XFF() {
        assertThat(resolver.resolve(request("1.2.3.4", "6.6.6.6"))).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("可信代理：XFF 取最右末跳（最近一跳代理写入的真实客户端）")
    void 可信代理_取XFF末跳() {
        assertThat(resolver.resolve(request("127.0.0.1", "6.6.6.6, 7.7.7.7"))).isEqualTo("7.7.7.7");
    }

    @Test
    @DisplayName("可信代理链：从右往左跳过可信代理，取第一个非可信地址")
    void 可信代理链_跳过可信() {
        assertThat(resolver.resolve(request("127.0.0.1", "6.6.6.6, 127.0.0.1"))).isEqualTo("6.6.6.6");
    }

    @Test
    @DisplayName("代理后伪造链：客户端伪造值在左、真实值在右——取右不中招")
    void 代理后伪造链_取真实末跳() {
        assertThat(resolver.resolve(request("127.0.0.1", "evil-fake, 6.6.6.6"))).isEqualTo("6.6.6.6");
    }

    @Test
    @DisplayName("XFF 缺失/unknown：退回 remoteAddr")
    void xff缺失_退回remote() {
        assertThat(resolver.resolve(request("127.0.0.1", null))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(request("127.0.0.1", "unknown"))).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("可信代理默认空（未配置）：即便来自 127.0.0.1 也忽略 XFF")
    void 未配置可信代理_忽略XFF() {
        ClientIpResolver fresh = new ClientIpResolver();
        ReflectionTestUtils.setField(fresh, "trustedProxiesRaw", "");
        fresh.initTrustedProxies();

        assertThat(fresh.resolve(request("127.0.0.1", "6.6.6.6"))).isEqualTo("127.0.0.1");
    }
}
