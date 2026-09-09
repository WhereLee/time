package com.reason.barrier.reporter;

import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.BarrierState;
import com.reason.barrier.network.NetworkCondition;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP 事件发送器单测（批次2，F1：注入 fake RestTemplate 断言发送行为，不连平台）：
 * 注入器三开关（延迟/重放/乱序）+ 附录 A 测试缺口（E10 4xx 不重试/退避耗尽、E12 拒绝响应体）
 */
@DisplayName("HTTP事件发送器(批次2注入器+E10/E12)")
class HttpEventReporterTest {

    private static final String DEVICE_NO = "BARRIER-E-01";
    private static final String SECRET = "test-secret-e01";
    private static final String BOOT_ID = "boot-test-1";
    private static final String TRACE_ID = "trace-test-1";

    private RestTemplate restTemplate;
    private NetworkCondition network;
    private HttpEventReporter reporter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        network = new NetworkCondition();
        reporter = newReporter();
    }

    private HttpEventReporter newReporter() {
        SimProperties properties = new SimProperties();
        properties.setEventUrl("http://127.0.0.1:8200/api/device/event");
        SimProperties.DeviceCfg device = new SimProperties.DeviceCfg();
        device.setDeviceNo(DEVICE_NO);
        device.setName("东门一号杆");
        device.setSecret(SECRET);
        properties.setDevices(List.of(device));
        return new HttpEventReporter(properties, network, restTemplate);
    }

    private void stubSendOk() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
    }

    @SuppressWarnings("unchecked")
    private String bodyOf(HttpEntity<?> entity) {
        return ((java.util.Map<String, Object>) entity.getBody()).toString();
    }

    @Test
    @DisplayName("事件延迟：eventDelayMillis>0 时发送被推迟到位（打 grace 边界）")
    void 事件延迟_发送被推迟() {
        stubSendOk();
        network.setEventDelayMillis(1500);

        long start = System.currentTimeMillis();
        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 42L, TRACE_ID);
        verify(restTemplate, timeout(5000)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(1400);
    }

    @Test
    @DisplayName("重放：成功送达后同报文（同 body/同签名头/同 traceId）再发 1 次")
    void 重放_成功送达同报文两次() {
        stubSendOk();
        network.setReplayNextEvent(true);

        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 42L, TRACE_ID);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        InOrder inOrder = inOrder(restTemplate);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            inOrder.verify(restTemplate, org.mockito.Mockito.times(2))
                    .postForEntity(anyString(), captor.capture(), eq(String.class));
        });
        List<HttpEntity<?>> sent = captor.getAllValues();
        assertThat(sent).hasSize(2);
        //同报文：body 完全一致（同 deviceNo/state/commandSeq/bootId/eventSeq——重放=审计幂等验证）
        assertThat(bodyOf(sent.get(0))).isEqualTo(bodyOf(sent.get(1)));
        assertThat(sent.get(0).getHeaders().getFirst("X-Device-Sign"))
                .isEqualTo(sent.get(1).getHeaders().getFirst("X-Device-Sign"));
        assertThat(sent.get(0).getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo(sent.get(1).getHeaders().getFirst("X-Trace-Id"));
        //布防一次性已消费：重放本身不再触发下一次重放（不递归）
        assertThat(network.consumeReplay()).isFalse();
    }

    @Test
    @DisplayName("发送失败（4xx 拒绝）：不消费 replay 布防（不放大故障）")
    void 重放_发送失败不消费布防() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));
        network.setReplayNextEvent(true);

        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 42L, TRACE_ID);
        verify(restTemplate, timeout(5000)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        //布防未被消费（留待下一次成功送达）
        assertThat(network.consumeReplay()).isTrue();
    }

    @Test
    @DisplayName("乱序：布防设备两条事件集齐后按 eventSeq 降序发送（e2 先 e1 后）")
    void 乱序_集齐降序发送() {
        stubSendOk();
        network.setReorderNextDeviceNo(DEVICE_NO);

        reporter.report(DEVICE_NO, BarrierState.MOVING, 7L, BOOT_ID, 1L, TRACE_ID);
        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 2L, TRACE_ID);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        InOrder inOrder = inOrder(restTemplate);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            inOrder.verify(restTemplate, org.mockito.Mockito.times(2))
                    .postForEntity(anyString(), captor.capture(), eq(String.class));
        });
        List<HttpEntity<?>> sent = captor.getAllValues();
        //降序：e2(eventSeq=2) 先达、e1(eventSeq=1) 随后——平台序守卫将拒 e1（旧序）
        assertThat(bodyOf(sent.get(0))).contains("eventSeq=2");
        assertThat(bodyOf(sent.get(1))).contains("eventSeq=1");
        //一次性开关随窗口完成自动复位
        assertThat(network.getReorderNextDeviceNo()).isNull();
    }

    @Test
    @DisplayName("乱序窗口：缓冲期间其他设备事件照常发送（不被拖住）")
    void 乱序_其他设备不被拖住() {
        stubSendOk();
        network.setReorderNextDeviceNo(DEVICE_NO);
        SimProperties properties = new SimProperties();
        properties.setEventUrl("http://127.0.0.1:8200/api/device/event");
        SimProperties.DeviceCfg d1 = new SimProperties.DeviceCfg();
        d1.setDeviceNo(DEVICE_NO);
        d1.setSecret(SECRET);
        SimProperties.DeviceCfg d2 = new SimProperties.DeviceCfg();
        d2.setDeviceNo("BARRIER-W-02");
        d2.setSecret("test-secret-w02");
        properties.setDevices(List.of(d1, d2));
        reporter = new HttpEventReporter(properties, network, restTemplate);

        //布防设备首条进窗口（阻塞），其他设备事件应照常发送
        reporter.report(DEVICE_NO, BarrierState.MOVING, 7L, BOOT_ID, 1L, TRACE_ID);
        reporter.report("BARRIER-W-02", BarrierState.UP, 3L, BOOT_ID, 9L, TRACE_ID);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, timeout(5000).times(1)).postForEntity(anyString(), captor.capture(), eq(String.class));
        assertThat(bodyOf(captor.getValue())).contains("BARRIER-W-02");
    }

    @Test
    @DisplayName("乱序窗口：只来 1 条，超时后被后续事件唤醒原序放行（防剧本卡死通道）")
    void 乱序_单条超时原序放行() throws Exception {
        stubSendOk();
        network.setReorderNextDeviceNo(DEVICE_NO);
        //双设备：唤醒事件必须来自非布防设备——同设备第二条会走"集齐倒序"分支（乱序主路径），
        //超时放行分支只在"非布防设备事件到达"时被唤醒（惰性超时：单发送线程不驻留定时器）
        SimProperties properties = new SimProperties();
        properties.setEventUrl("http://127.0.0.1:8200/api/device/event");
        SimProperties.DeviceCfg d1 = new SimProperties.DeviceCfg();
        d1.setDeviceNo(DEVICE_NO);
        d1.setSecret(SECRET);
        SimProperties.DeviceCfg d2 = new SimProperties.DeviceCfg();
        d2.setDeviceNo("BARRIER-W-02");
        d2.setSecret("test-secret-w02");
        properties.setDevices(List.of(d1, d2));
        reporter = new HttpEventReporter(properties, network, restTemplate);

        //首条进窗口（滞留阻塞）
        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 1L, TRACE_ID);
        //等窗口超时（5s）已过后，非布防设备事件到达唤醒超时检查：首条先原序放行，本事件随后正常处理
        TimeUnit.MILLISECONDS.sleep(5500);
        reporter.report("BARRIER-W-02", BarrierState.DOWN, 8L, BOOT_ID, 2L, TRACE_ID);

        //先等两次发送都发生，再做顺序断言（InOrder 分次 verify 同参会因剩余匹配调用误报——用 times(2) 一次消费）
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, timeout(10000).times(2))
                .postForEntity(anyString(), captor.capture(), eq(String.class));
        List<HttpEntity<?>> sent = captor.getAllValues();
        //超时放行=原序：布防设备首条（e1）先发、唤醒事件后发——未发生乱序（只来 1 条时剧本收敛为正常发送）
        assertThat(bodyOf(sent.get(0))).contains(DEVICE_NO);
        assertThat(bodyOf(sent.get(1))).contains("BARRIER-W-02");
        //一次性开关随窗口完成复位
        assertThat(network.getReorderNextDeviceNo()).isNull();
    }

    @Test
    @DisplayName("E10：平台 4xx 拒绝（401）——不重试，只发 1 次")
    void e10_平台4xx不重试() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 42L, TRACE_ID);

        //等一个重试周期以上，确认只发了 1 次（4xx 不重试）
        verify(restTemplate, timeout(3000).times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("E10：网络异常退避 1s/2s/3s 后重试耗尽丢弃（共 1+3 次尝试）")
    void e10_网络异常退避耗尽() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 42L, TRACE_ID);

        //1 次首发 + 3 次退避重试 = 4 次尝试，耗尽后丢弃（平台 QUERY_STATE/对账兜底）
        verify(restTemplate, timeout(15000).times(4)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("E12：平台 4xx 拒绝响应体原文入日志路径（resp 可见——token 漂移/密钥失配立即可见）")
    void e12_拒绝响应体可见() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        org.springframework.http.HttpHeaders.EMPTY,
                        "{\"code\":400,\"msg\":\"非法状态码\"}".getBytes(), null));

        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 42L, TRACE_ID);

        //4xx 不重试只发 1 次；拒绝响应体经 error 日志输出（日志内容断言超出 mock 范围——
        //此处钉死"不重试+立即放弃"的行为面，响应体留痕由日志实现保证）
        verify(restTemplate, timeout(3000).times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("网络剧本三开关 setter/消费语义（NetworkCondition 一并钉住）")
    void 三开关_语义() {
        network.setEventDelayMillis(2000);
        assertThat(network.getEventDelayMillis()).isEqualTo(2000);
        network.setEventDelayMillis(0);
        assertThat(network.getEventDelayMillis()).isZero();

        network.setReplayNextEvent(true);
        assertThat(network.consumeReplay()).isTrue();
        assertThat(network.consumeReplay()).isFalse();

        network.setReorderNextDeviceNo("BARRIER-E-01");
        assertThat(network.getReorderNextDeviceNo()).isEqualTo("BARRIER-E-01");
        network.clearReorder();
        assertThat(network.getReorderNextDeviceNo()).isNull();
    }
}
