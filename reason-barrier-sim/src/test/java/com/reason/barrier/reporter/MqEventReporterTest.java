package com.reason.barrier.reporter;

import com.reason.barrier.config.DeviceSignature;
import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.BarrierState;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MQ 事件发送器单测（阶段2，F1：注入 fake producer，不连 broker）：
 * 信封构造（报文体同构 HTTP body + 签名进 message property）、
 * 失败退避重试且保序（D5）、缓冲满丢弃最旧（平台 QUERY_STATE 兜底）
 */
@DisplayName("MQ事件发送器(阶段2双写)")
class MqEventReporterTest {

    private static final String DEVICE_NO = "BARRIER-E-01";
    private static final String SECRET = "test-secret-e01";
    private static final String BOOT_ID = "boot-test-1";

    private Producer producer;
    private MqEventReporter reporter;

    @BeforeEach
    void setUp() {
        producer = mock(Producer.class);
    }

    @AfterEach
    void tearDown() {
        if (reporter != null) {
            reporter.shutdown();
        }
    }

    private MqEventReporter newReporter(int bufferSize) {
        SimProperties properties = new SimProperties();
        properties.getMq().setEndpoint("127.0.0.1:8081");
        properties.getMq().setTopic("device-event");
        properties.getMq().setBufferSize(bufferSize);
        SimProperties.DeviceCfg device = new SimProperties.DeviceCfg();
        device.setDeviceNo(DEVICE_NO);
        device.setName("东门一号杆");
        device.setSecret(SECRET);
        properties.setDevices(List.of(device));
        return new MqEventReporter(properties, producer);
    }

    @Test
    @DisplayName("信封构造：报文体=协议v2事件JSON（同构HTTP body），签名进 message property（canonical 不变）")
    void 信封构造_签名进property() {
        reporter = newReporter(500);
        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 42L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(producer).send(captor.capture()));
        Message message = captor.getValue();

        assertThat(message.getTopic()).isEqualTo("device-event");
        //报文体与 HTTP body 完全同构：五字段，commandSeq 非空时原样携带
        String body = StandardCharsets.UTF_8.decode(message.getBody().duplicate()).toString();
        assertThat(body).contains("\"deviceNo\":\"BARRIER-E-01\"");
        assertThat(body).contains("\"state\":1");
        assertThat(body).contains("\"commandSeq\":7");
        assertThat(body).contains("\"bootId\":\"boot-test-1\"");
        assertThat(body).contains("\"eventSeq\":42");
        //签名进 message property：X-Device-Sign == HMAC(secret, canonicalEvent(...))
        assertThat(message.getProperties().get("X-Device-No")).isEqualTo(DEVICE_NO);
        String expectedSign = DeviceSignature.sign(SECRET,
                DeviceSignature.canonicalEvent(DEVICE_NO, 1, 7L, BOOT_ID, 42L));
        assertThat(message.getProperties().get("X-Device-Sign")).isEqualTo(expectedSign);
    }

    @Test
    @DisplayName("commandSeq 为空（外力改态）：报文体携带 null 字段，签名 canonical 空串位")
    void 信封构造_commandSeq可空() {
        reporter = newReporter(500);
        reporter.report(DEVICE_NO, BarrierState.DOWN, null, BOOT_ID, 43L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(producer).send(captor.capture()));
        Message message = captor.getValue();

        String body = StandardCharsets.UTF_8.decode(message.getBody().duplicate()).toString();
        assertThat(body).contains("\"commandSeq\":null");
        String expectedSign = DeviceSignature.sign(SECRET,
                DeviceSignature.canonicalEvent(DEVICE_NO, 2, null, BOOT_ID, 43L));
        assertThat(message.getProperties().get("X-Device-Sign")).isEqualTo(expectedSign);
    }

    @Test
    @DisplayName("发送失败退避重试：同事件重发至成功，后续事件不越过（D5 保序）")
    void 失败重试_保序() throws Exception {
        reporter = newReporter(500);
        //前两次发送失败（broker 抖动），第三次成功
        when(producer.send(any(Message.class)))
                .thenThrow(new RuntimeException("broker unreachable"))
                .thenThrow(new RuntimeException("broker unreachable"))
                .thenReturn(null);

        reporter.report(DEVICE_NO, BarrierState.MOVING, 7L, BOOT_ID, 1L);
        reporter.report(DEVICE_NO, BarrierState.UP, 7L, BOOT_ID, 2L);

        //e1 重试 3 次（退避 1s+2s）后成功，e2 随后——全程保序
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                verify(producer, atLeast(4)).send(captor.capture()));
        List<Message> sent = captor.getAllValues();
        //前 3 次都是 e1（eventSeq=1），第 4 次才是 e2（eventSeq=2）——失败事件持留队首不越过
        for (int i = 0; i < 3; i++) {
            assertThat(StandardCharsets.UTF_8.decode(sent.get(i).getBody().duplicate()).toString())
                    .contains("\"eventSeq\":1");
        }
        assertThat(StandardCharsets.UTF_8.decode(sent.get(3).getBody().duplicate()).toString())
                .contains("\"eventSeq\":2");
    }

    @Test
    @DisplayName("缓冲队列满：丢弃最旧未发事件（记 error 交平台兜底），新事件入队")
    void 缓冲满_丢弃最旧() throws Exception {
        reporter = newReporter(2);
        CountDownLatch sendEntered = new CountDownLatch(1);
        CountDownLatch sendRelease = new CountDownLatch(1);
        //首条发送阻塞（模拟 broker 慢），队列才有机会积满
        when(producer.send(any(Message.class))).thenAnswer(invocation -> {
            sendEntered.countDown();
            assertThat(sendRelease.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        });

        reporter.report(DEVICE_NO, BarrierState.MOVING, 1L, BOOT_ID, 1L);
        assertThat(sendEntered.await(10, TimeUnit.SECONDS)).isTrue();
        //e1 在途阻塞；e2/e3 占满队列（容量 2）；e4 挤掉最旧的 e2
        reporter.report(DEVICE_NO, BarrierState.UP, 1L, BOOT_ID, 2L);
        reporter.report(DEVICE_NO, BarrierState.MOVING, 2L, BOOT_ID, 3L);
        reporter.report(DEVICE_NO, BarrierState.UP, 2L, BOOT_ID, 4L);
        sendRelease.countDown();

        CopyOnWriteArrayList<String> sentBodies = new CopyOnWriteArrayList<>();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(producer, atLeast(3)).send(captor.capture());
            captor.getAllValues().forEach(m ->
                    sentBodies.add(StandardCharsets.UTF_8.decode(m.getBody().duplicate()).toString()));
            assertThat(sentBodies).anySatisfy(b -> assertThat(b).contains("\"eventSeq\":1"))
                    .anySatisfy(b -> assertThat(b).contains("\"eventSeq\":3"))
                    .anySatisfy(b -> assertThat(b).contains("\"eventSeq\":4"));
        });
        //e2（最旧）被丢弃，从未发送
        assertThat(sentBodies).noneSatisfy(b -> assertThat(b).contains("\"eventSeq\":2"));
    }

    @Test
    @DisplayName("设备未配置密钥：毒事件丢弃不重试（与 HTTP 通道同语义）")
    void 密钥缺失_丢弃不重试() throws Exception {
        reporter = newReporter(500);
        reporter.report("BARRIER-UNKNOWN", BarrierState.UP, 1L, BOOT_ID, 1L);

        //等待一个重试周期以上，确认从未发起发送
        TimeUnit.MILLISECONDS.sleep(1500);
        verify(producer, never()).send(any(Message.class));
    }
}
