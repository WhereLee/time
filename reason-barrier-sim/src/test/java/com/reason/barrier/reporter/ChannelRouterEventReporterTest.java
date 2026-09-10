package com.reason.barrier.reporter;

import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.BarrierState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 事件通道路由单测（批次3，D-A 三态开关）：
 * dual=双通道均投递（同一 traceId 对照）、http=仅 HTTP、mq=仅 MQ、
 * 配置错位 fail-fast（非法取值 / mq 形态但 MQ 通道缺失）
 */
@DisplayName("事件通道路由(批次3三态)")
class ChannelRouterEventReporterTest {

    private static final String DEVICE_NO = "BARRIER-B-01";
    private static final String BOOT_ID = "boot-test-1";
    private static final String TRACE = "trace-test-0001";

    private final SimProperties properties = new SimProperties();
    private final HttpEventReporter httpReporter = mock(HttpEventReporter.class);
    private final MqEventReporter mqReporter = mock(MqEventReporter.class);
    private final ObjectProvider<MqEventReporter> mqProvider = mock(ObjectProvider.class);

    private ChannelRouterEventReporter newRouter() {
        return new ChannelRouterEventReporter(properties, httpReporter, mqProvider);
    }

    private void reportVia(ChannelRouterEventReporter router) {
        router.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 11L, TRACE);
    }

    @Test
    @DisplayName("默认取值：mq（批次3 落档终态——HTTP 退役为降级开关）")
    void defaultChannelIsMq() {
        assertThat(new SimProperties().getEventChannel()).isEqualTo("mq");
    }

    @Test
    @DisplayName("dual（对照期）：HTTP/MQ 双通道均投递，同一 traceId")
    void dualReportsBothChannels() {
        //批次3 落档后默认已是 mq——dual 为对照/注入剧本场景，显式指定
        properties.setEventChannel("dual");
        //ifAvailable 语义：Bean 存在时执行 Consumer——stub 模拟（mq.enabled=true 场景；void 方法用 doAnswer）
        doAnswer(invocation -> {
            Consumer<MqEventReporter> consumer = invocation.getArgument(0);
            consumer.accept(mqReporter);
            return null;
        }).when(mqProvider).ifAvailable(any());

        reportVia(newRouter());

        ArgumentCaptor<String> httpTrace = ArgumentCaptor.forClass(String.class);
        verify(httpReporter).report(any(), any(), any(), any(), anyLong(), httpTrace.capture());
        ArgumentCaptor<String> mqTrace = ArgumentCaptor.forClass(String.class);
        verify(mqReporter).report(any(), any(), any(), any(), anyLong(), mqTrace.capture());
        assertThat(httpTrace.getValue()).isEqualTo(TRACE);
        assertThat(mqTrace.getValue()).isEqualTo(TRACE);
    }

    @Test
    @DisplayName("http（降级形态）：仅 HTTP 投递，MQ 通道零交互")
    void httpReportsOnlyHttp() {
        properties.setEventChannel("http");

        reportVia(newRouter());

        verify(httpReporter).report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 11L, TRACE);
        verifyNoInteractions(mqProvider);
    }

    @Test
    @DisplayName("mq（终态）：仅 MQ 投递，HTTP 通道零交互")
    void mqReportsOnlyMq() {
        properties.setEventChannel("mq");
        when(mqProvider.getIfAvailable()).thenReturn(mqReporter);
        when(mqProvider.getObject()).thenReturn(mqReporter);

        reportVia(newRouter());

        verify(mqReporter).report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 11L, TRACE);
        verifyNoInteractions(httpReporter);
    }

    @Test
    @DisplayName("fail-fast：mq 形态但 sim.mq.enabled=false（通道缺失）——构造即抛错")
    void mqWithoutMqChannelFailsFast() {
        properties.setEventChannel("mq");
        //getIfAvailable 默认返回 null = mq.enabled=false 时条件 Bean 不存在

        assertThatThrownBy(this::newRouter)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("配置错位");
        verify(httpReporter, never()).report(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("fail-fast：event-channel 非法取值——构造即抛错，不静默降级")
    void invalidChannelFailsFast() {
        properties.setEventChannel("kafka");

        assertThatThrownBy(this::newRouter)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法取值");
    }
}
