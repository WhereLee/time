package com.reason.common.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TraceIdFilter 单元测试：透传复用/自生成/MDC 清理防串号
 */
@DisplayName("traceId 过滤器")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private String doFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> captured = new ArrayList<>();
        filter.doFilter(request, response, (req, res) ->
                captured.add(MDC.get(TraceIdFilter.MDC_KEY)));  // 链路执行时刻读取 MDC
        return captured.isEmpty() ? null : captured.get(0);
    }

    @Test
    @DisplayName("无上游 traceId：生成 32 位 hex，写入 MDC 与响应头，结束后 MDC 清理")
    void 无上游_自生成并回写响应头_结束后清理() throws Exception {
        String inChain = doFilter(new MockHttpServletRequest("GET", "/api/sys/user/list"));

        assertThat(inChain).matches(Pattern.compile("[0-9a-f]{32}"));
        //finally 清理防串号：请求结束后 MDC 无残留（Tomcat 线程复用场景）
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("上游透传 X-Trace-Id：复用不重新生成")
    void 上游透传_复用traceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "abc123");

        String inChain = doFilter(request);

        assertThat(inChain).isEqualTo("abc123");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("响应头回写 X-Trace-Id（报障定位用）")
    void 响应头回写() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), response,
                (req, res) -> {});

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .matches(Pattern.compile("[0-9a-f]{32}"));
    }

    @Test
    @DisplayName("链路抛异常时 MDC 同样被清理（finally 语义）")
    void 异常路径_MDC同样清理() {
        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest("GET", "/api/x"),
                new MockHttpServletResponse(),
                (req, res) -> {
                    throw new RuntimeException("boom");
                }))
                .isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("生成器：32 位 hex 且不重复")
    void 生成器格式与随机性() {
        assertThat(TraceIdFilter.generateTraceId()).matches(Pattern.compile("[0-9a-f]{32}"));
        assertThat(TraceIdFilter.generateTraceId()).isNotEqualTo(TraceIdFilter.generateTraceId());
    }
}
