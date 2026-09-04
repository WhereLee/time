package com.reason.modules.parking.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 设备通道鉴权过滤器测试：路径判定 + 令牌比对（含未配置安全默认）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备通道鉴权")
class DeviceAuthFilterTest {

    @Mock
    private FilterChain chain;

    private DeviceAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DeviceAuthFilter();
        ReflectionTestUtils.setField(filter, "accessToken", "sim-token-test");
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContextPath("/api");
        req.setRequestURI(uri);
        return req;
    }

    @Test
    @DisplayName("设备通道 + 正确令牌 → 放行")
    void 设备通道令牌正确放行() throws Exception {
        MockHttpServletRequest req = request("/api/device/parking/entry");
        req.addHeader("X-Device-Token", "sim-token-test");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("设备通道 + 错误令牌 → 401 JSON 且不透传")
    void 设备通道令牌错误拒绝() throws Exception {
        MockHttpServletRequest req = request("/api/device/parking/exit");
        req.addHeader("X-Device-Token", "wrong");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verifyNoInteractions(chain);
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("无效的设备令牌");
    }

    @Test
    @DisplayName("设备通道 + 无令牌 → 401 拒绝")
    void 设备通道无令牌拒绝() throws Exception {
        MockHttpServletRequest req = request("/api/device/parking/entry");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verifyNoInteractions(chain);
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("非设备路径 → 无论令牌与否透传（不拦截管理端请求）")
    void 非设备路径透传() throws Exception {
        MockHttpServletRequest req = request("/api/parking/space/page");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("未配置 access-token（安全默认）→ 一切设备请求拒绝")
    void 未配置令牌拒绝一切() throws Exception {
        filter = new DeviceAuthFilter();
        ReflectionTestUtils.setField(filter, "accessToken", "");
        MockHttpServletRequest req = request("/api/device/parking/entry");
        req.addHeader("X-Device-Token", "anything");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verifyNoInteractions(chain);
        assertThat(resp.getStatus()).isEqualTo(401);
    }
}
