package com.reason.modules.device.mq;

import com.alibaba.fastjson2.JSON;
import com.reason.common.exception.RRException;
import com.reason.modules.device.config.DeviceChannelAuthenticator;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.form.DeviceEventForm;
import com.reason.modules.device.service.DeviceEventService;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 设备事件 MQ 消费编排单测（阶段2，F1：构造消息对象直调处理函数，不连 broker）：
 * 验签/编排复用核验（authenticateEvent + handleStateEvent 被调）、
 * 失败分级（验签失败/毒消息→ACK 丢弃；业务瞬时异常→RETRY 等重投）
 */
@DisplayName("设备事件MQ消费编排(阶段2)")
@ExtendWith(MockitoExtension.class)
class DeviceEventMqConsumerTest {

    @Mock
    private DeviceChannelAuthenticator authenticator;
    @Mock
    private DeviceEventService deviceEventService;

    private DeviceEventMqConsumer consumer;

    @BeforeEach
    void setUp() {
        DeviceChannelProperties properties = new DeviceChannelProperties();
        properties.getMq().setEndpoint("127.0.0.1:8081");
        properties.getMq().setTopic("device-event");
        properties.getMq().setConsumerGroup("platform-device-event");
        consumer = new DeviceEventMqConsumer(properties, authenticator, deviceEventService);
    }

    /**
     * 构造消息对象：body=协议 v2 事件 JSON，property 携 X-Device-No/X-Device-Sign
     */
    private MessageView messageOf(String deviceNo, Integer state, Long commandSeq,
                                  String bootId, Long eventSeq, String propDeviceNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("deviceNo", deviceNo);
        body.put("state", state);
        body.put("commandSeq", commandSeq);
        body.put("bootId", bootId);
        body.put("eventSeq", eventSeq);
        MessageView message = mock(MessageView.class);
        org.mockito.Mockito.lenient().when(message.getMessageId()).thenReturn(mock(MessageId.class));
        org.mockito.Mockito.lenient().when(message.getBody())
                .thenReturn(ByteBuffer.wrap(JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8)));
        Map<String, String> props = new HashMap<>();
        if (propDeviceNo != null) {
            props.put("X-Device-No", propDeviceNo);
        }
        props.put("X-Device-Sign", "fake-sign");
        org.mockito.Mockito.lenient().when(message.getProperties()).thenReturn(props);
        return message;
    }

    @Test
    @DisplayName("合法事件：验签通过→编排被调（复用核验）→ACK")
    void 合法事件_验签编排被调_ACK() {
        MessageView message = messageOf("BARRIER-E-01", 1, 7L, "boot-1", 42L, "BARRIER-E-01");

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.ACK);
        verify(authenticator).authenticateEvent("BARRIER-E-01", 1, 7L, "boot-1", 42L, "fake-sign");
        ArgumentCaptor<DeviceEventForm> captor = ArgumentCaptor.forClass(DeviceEventForm.class);
        verify(deviceEventService).handleStateEvent(captor.capture());
        DeviceEventForm form = captor.getValue();
        assertThat(form.getDeviceNo()).isEqualTo("BARRIER-E-01");
        assertThat(form.getState()).isEqualTo(1);
        assertThat(form.getCommandSeq()).isEqualTo(7L);
        assertThat(form.getBootId()).isEqualTo("boot-1");
        assertThat(form.getEventSeq()).isEqualTo(42L);
    }

    @Test
    @DisplayName("验签失败（401 语义）：毒消息 ACK 丢弃，不进编排不重投")
    void 验签失败_ACK丢弃() {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "设备签名无效"))
                .when(authenticator).authenticateEvent(any(), any(), any(), any(), any(), any());
        MessageView message = messageOf("BARRIER-E-01", 1, 7L, "boot-1", 42L, "BARRIER-E-01");

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.ACK);
        verify(deviceEventService, never()).handleStateEvent(any());
    }

    @Test
    @DisplayName("非法状态码（协议垃圾）：ACK 丢弃，不进编排")
    void 非法状态码_ACK丢弃() {
        MessageView message = messageOf("BARRIER-E-01", 99, 7L, "boot-1", 42L, "BARRIER-E-01");

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.ACK);
        verify(deviceEventService, never()).handleStateEvent(any());
    }

    @Test
    @DisplayName("消息体非 JSON（协议垃圾）：ACK 丢弃，验签不进")
    void 协议垃圾_ACK丢弃() {
        MessageView message = mock(MessageView.class);
        org.mockito.Mockito.lenient().when(message.getMessageId()).thenReturn(mock(MessageId.class));
        org.mockito.Mockito.lenient().when(message.getBody())
                .thenReturn(ByteBuffer.wrap("not-a-json".getBytes(StandardCharsets.UTF_8)));
        org.mockito.Mockito.lenient().when(message.getProperties()).thenReturn(Map.of());

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.ACK);
        verify(authenticator, never()).authenticateEvent(any(), any(), any(), any(), any(), any());
        verify(deviceEventService, never()).handleStateEvent(any());
    }

    @Test
    @DisplayName("缺必填字段（eventSeq 缺失）：ACK 丢弃，验签不进")
    void 缺必填字段_ACK丢弃() {
        MessageView message = messageOf("BARRIER-E-01", 1, 7L, "boot-1", null, "BARRIER-E-01");

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.ACK);
        verify(authenticator, never()).authenticateEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("property 设备号与报文体不一致（信封被拼改）：ACK 丢弃")
    void 设备号不一致_ACK丢弃() {
        MessageView message = messageOf("BARRIER-E-01", 1, 7L, "boot-1", 42L, "BARRIER-W-02");

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.ACK);
        verify(authenticator, never()).authenticateEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("业务拒绝（未登记设备 RRException，HTTP 400 同语义）：毒消息 ACK 丢弃不重投")
    void 业务拒绝_ACK丢弃() {
        doThrow(new RRException("未登记的设备上报事件: BARRIER-E-01"))
                .when(deviceEventService).handleStateEvent(any());
        MessageView message = messageOf("BARRIER-E-01", 1, 7L, "boot-1", 42L, "BARRIER-E-01");

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.ACK);
    }

    @Test
    @DisplayName("业务处理瞬时异常（DB 抖动）：RETRY 不 ack，等 broker 重投")
    void 瞬时异常_RETRY等重投() {
        doThrow(new RecoverableDataAccessException("DB connection reset"))
                .when(deviceEventService).handleStateEvent(any());
        MessageView message = messageOf("BARRIER-E-01", 1, 7L, "boot-1", 42L, "BARRIER-E-01");

        DeviceEventMqConsumer.Outcome outcome = consumer.process(message);

        assertThat(outcome).isEqualTo(DeviceEventMqConsumer.Outcome.RETRY);
    }
}
