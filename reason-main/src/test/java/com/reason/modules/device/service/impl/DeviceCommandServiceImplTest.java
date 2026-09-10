package com.reason.modules.device.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.common.exception.RRException;
import com.reason.common.filter.TraceIdFilter;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.config.DeviceSignature;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.TriggerType;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceCommandService;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备指令服务单测（A2 补网）：下行指令全链路——档案校验/seq 分配/流水记账/
 * HMAC 下行签名头/traceId 透传/HTTP 结果语义（code≠0 拒绝、401 拒绝、网络失败）
 * 设备侧用 JDK 内置 HttpServer 真实回环（非 mock HTTP 客户端，签名与报文可断言）。
 */
@DisplayName("设备指令服务(A2 补网)")
@ExtendWith(MockitoExtension.class)
class DeviceCommandServiceImplTest {

    private static final String DEVICE_NO = "BARRIER-E-01";
    private static final String SECRET = "aabbccddeeff00112233445566778899";
    /** 连接必被拒的地址（网络层失败路径） */
    private static final String UNREACHABLE_URL = "http://127.0.0.1:1";

    /**
     * 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存
     * （见 document/pitfalls/mybatis-plus-lambda-cache-unit-test.md）
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeviceRecordEntity.class);
    }

    @Mock
    private DeviceRecordDao recordDao;
    @Mock
    private DeviceCommandLogService commandLogService;

    private DeviceChannelProperties properties;
    private DeviceCommandService service;

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastSignHeader = new AtomicReference<>();
    private final AtomicReference<String> lastTraceHeader = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"code\":0}";

    @BeforeEach
    void setUp() throws IOException {
        // 设备侧回环：捕获请求报文/签名头/trace 头，按用例预设应答
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastSignHeader.set(exchange.getRequestHeaders().getFirst("X-Device-Sign"));
            lastTraceHeader.set(exchange.getRequestHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            // 必须显式声明 JSON：RestTemplate 按 Content-Type 选转换器（默认 octet-stream 无法映射 Map）
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        properties = new DeviceChannelProperties();
        properties.setSimBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        service = new DeviceCommandServiceImpl(recordDao, properties, commandLogService);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        MDC.clear();
    }

    private DeviceRecordEntity deviceRecord(String secret) {
        DeviceRecordEntity record = new DeviceRecordEntity();
        record.setDeviceNo(DEVICE_NO);
        record.setDeviceSecret(secret);
        return record;
    }

    private DeviceCommandLogEntity pendingEntity(long seq) {
        DeviceCommandLogEntity entity = new DeviceCommandLogEntity();
        entity.setCommandId(100L + seq);
        entity.setDeviceNo(DEVICE_NO);
        entity.setCommandSeq(seq);
        entity.setRetryCount(0);
        return entity;
    }

    private void stubRecordExists() {
        when(recordDao.selectOne(any())).thenReturn(deviceRecord(SECRET));
    }

    private void stubNewCommand(long seq, TriggerType trigger) {
        when(commandLogService.nextSeq(DEVICE_NO)).thenReturn(seq);
        when(commandLogService.recordPending(eq(DEVICE_NO), anyString(), eq(seq), eq(trigger)))
                .thenReturn(pendingEntity(seq));
    }

    // ---------- 手动下发（open/close）----------

    @Test
    @DisplayName("手动下发成功：记流水(MANUAL) + 下行 HMAC 签名头有效 + traceId 沿用 MDC")
    void 手动下发_成功_签名与链路头() {
        stubRecordExists();
        stubNewCommand(1L, TriggerType.MANUAL);
        MDC.put(TraceIdFilter.MDC_KEY, "trace-abc");

        service.open(DEVICE_NO);

        verify(commandLogService, never()).markSendFailed(any());
        assertThat(lastSignHeader.get())
                .isEqualTo(DeviceSignature.sign(SECRET, DeviceSignature.canonicalCommand(DEVICE_NO, "OPEN", 1L)));
        assertThat(lastTraceHeader.get()).isEqualTo("trace-abc");
        JSONObject body = JSON.parseObject(lastBody.get());
        assertThat(body.getString("deviceNo")).isEqualTo(DEVICE_NO);
        assertThat(body.getString("action")).isEqualTo("OPEN");
        assertThat(body.getLongValue("commandSeq")).isEqualTo(1L);
    }

    @Test
    @DisplayName("手动下发 close：action=CLOSE；无 MDC 链路号时自动生成（下行请求不缺链路头）")
    void 手动下发_close_自动生成链路号() {
        stubRecordExists();
        stubNewCommand(2L, TriggerType.MANUAL);

        service.close(DEVICE_NO);

        assertThat(lastTraceHeader.get()).isNotBlank();
        assertThat(JSON.parseObject(lastBody.get()).getString("action")).isEqualTo("CLOSE");
        verify(commandLogService, never()).markSendFailed(any());
    }

    @Test
    @DisplayName("手动下发：档案不存在 -> 拒绝下发，不分配 seq 不记账")
    void 手动下发_未登记_拒绝() {
        when(recordDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.open(DEVICE_NO))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("设备未登记");
        verify(commandLogService, never()).nextSeq(anyString());
    }

    @Test
    @DisplayName("手动下发：档案存在但密钥缺失 -> fail secure，流水记 SEND_FAILED 并抛错（人在等结果）")
    void 手动下发_密钥缺失_记失败并抛() {
        when(recordDao.selectOne(any())).thenReturn(deviceRecord(null));
        stubNewCommand(1L, TriggerType.MANUAL);

        assertThatThrownBy(() -> service.open(DEVICE_NO))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("设备密钥缺失");
        verify(commandLogService).markSendFailed(101L);
    }

    @Test
    @DisplayName("手动下发：设备回 code≠0（明确拒绝）-> 流水 SEND_FAILED + 抛出")
    void 手动下发_设备拒绝() {
        stubRecordExists();
        stubNewCommand(1L, TriggerType.MANUAL);
        responseBody = "{\"code\":1,\"msg\":\"状态不合法\"}";

        assertThatThrownBy(() -> service.open(DEVICE_NO))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("设备拒绝指令");
        verify(commandLogService).markSendFailed(101L);
    }

    @Test
    @DisplayName("手动下发：sim 4xx（401 验签失败）= 明确拒绝，不再归为无响应")
    void 手动下发_HTTP401_明确拒绝() {
        stubRecordExists();
        stubNewCommand(1L, TriggerType.MANUAL);
        responseStatus = 401;
        responseBody = "{\"error\":\"unauthorized\"}";

        assertThatThrownBy(() -> service.open(DEVICE_NO))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("HTTP 401");
        verify(commandLogService).markSendFailed(101L);
    }

    @Test
    @DisplayName("手动下发：网络不可达 -> 无响应语义，流水 SEND_FAILED + 抛出")
    void 手动下发_网络失败() {
        stubRecordExists();
        stubNewCommand(1L, TriggerType.MANUAL);
        properties.setSimBaseUrl(UNREACHABLE_URL);

        assertThatThrownBy(() -> service.open(DEVICE_NO))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("设备无响应");
        verify(commandLogService).markSendFailed(101L);
    }

    // ---------- 规则下发与重试 ----------

    @Test
    @DisplayName("规则下发成功：记流水(AUTO_RULE) 不抛")
    void 规则下发_成功() {
        stubRecordExists();
        stubNewCommand(7L, TriggerType.AUTO_RULE);

        service.sendByRule(DEVICE_NO, "OPEN");

        verify(commandLogService, never()).markSendFailed(any());
    }

    @Test
    @DisplayName("规则下发失败：单台失败不抛（记账留痕，下轮对账自然重试）")
    void 规则下发_失败不抛() {
        stubRecordExists();
        stubNewCommand(7L, TriggerType.AUTO_RULE);
        responseBody = "{\"code\":1,\"msg\":\"拒绝\"}";

        assertThatCode(() -> service.sendByRule(DEVICE_NO, "OPEN")).doesNotThrowAnyException();
        verify(commandLogService).markSendFailed(107L);
    }

    @Test
    @DisplayName("超时重试成功：重发同 seq，重试计数 +1")
    void 重试_成功计数() {
        stubRecordExists();
        DeviceCommandLogEntity pending = pendingEntity(7L);

        service.retryPending(pending);

        verify(commandLogService).markRetried(107L);
        verify(commandLogService, never()).markSendFailed(any());
    }

    @Test
    @DisplayName("超时重试被设备拒绝：终止为 SEND_FAILED 且不计重试（烧次数只会误告警）")
    void 重试_被拒终止() {
        stubRecordExists();
        DeviceCommandLogEntity pending = pendingEntity(7L);
        responseBody = "{\"code\":1,\"msg\":\"拒绝：曾被拒 seq 重试\"}";

        service.retryPending(pending);

        verify(commandLogService).markSendFailed(107L);
        verify(commandLogService, never()).markRetried(any());
    }

    @Test
    @DisplayName("超时重试网络失败：仍计重试次数（防无限重试），不终止")
    void 重试_网络失败计数() {
        stubRecordExists();
        DeviceCommandLogEntity pending = pendingEntity(7L);
        properties.setSimBaseUrl(UNREACHABLE_URL);

        service.retryPending(pending);

        verify(commandLogService).markRetried(107L);
        verify(commandLogService, never()).markSendFailed(any());
    }

    // ---------- 状态查询（QUERY_STATE）----------

    @Test
    @DisplayName("状态查询成功：实况字段全映射 + QUERY 签名头 + 链路号不缺")
    void 状态查询_成功全字段() {
        stubRecordExists();
        responseBody = "{\"code\":0,\"data\":{\"deviceNo\":\"BARRIER-E-01\",\"state\":1,"
                + "\"bootId\":\"b1\",\"eventSeq\":8,\"lastCommandSeq\":7}}";

        DeviceCommandService.QueryResult result = service.queryState(DEVICE_NO);

        assertThat(result).isNotNull();
        assertThat(result.getDeviceNo()).isEqualTo(DEVICE_NO);
        assertThat(result.getState()).isEqualTo(1);
        assertThat(result.getBootId()).isEqualTo("b1");
        assertThat(result.getEventSeq()).isEqualTo(8L);
        assertThat(result.getLastCommandSeq()).isEqualTo(7L);
        assertThat(lastSignHeader.get())
                .isEqualTo(DeviceSignature.sign(SECRET, DeviceSignature.canonicalCommand(DEVICE_NO, "QUERY", 0)));
        assertThat(lastTraceHeader.get()).isNotBlank();
    }

    @Test
    @DisplayName("状态查询：应答可空字段只给 state -> 其余字段 null 映射（不炸）")
    void 状态查询_可空字段映射() {
        stubRecordExists();
        responseBody = "{\"code\":0,\"data\":{\"state\":2}}";

        DeviceCommandService.QueryResult result = service.queryState(DEVICE_NO);

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(2);
        assertThat(result.getDeviceNo()).isNull();
        assertThat(result.getBootId()).isNull();
        assertThat(result.getEventSeq()).isNull();
        assertThat(result.getLastCommandSeq()).isNull();
    }

    @Test
    @DisplayName("状态查询：密钥缺失 -> 取消查询返回 null，不发请求")
    void 状态查询_密钥缺失() {
        when(recordDao.selectOne(any())).thenReturn(null);

        assertThat(service.queryState(DEVICE_NO)).isNull();
        assertThat(lastBody.get()).isNull();
    }

    @Test
    @DisplayName("状态查询：设备回 code≠0 -> null（与'查到非目标态'区分）")
    void 状态查询_被拒() {
        stubRecordExists();
        responseBody = "{\"code\":500,\"msg\":\"busy\"}";

        assertThat(service.queryState(DEVICE_NO)).isNull();
    }

    @Test
    @DisplayName("状态查询：应答缺 data/state -> null（调用方走重试路径）")
    void 状态查询_应答缺字段() {
        stubRecordExists();
        responseBody = "{\"code\":0,\"data\":{\"deviceNo\":\"BARRIER-E-01\"}}";

        assertThat(service.queryState(DEVICE_NO)).isNull();
    }

    @Test
    @DisplayName("状态查询：data 非对象 -> null")
    void 状态查询_应答格式异常() {
        stubRecordExists();
        responseBody = "{\"code\":0,\"data\":\"oops\"}";

        assertThat(service.queryState(DEVICE_NO)).isNull();
    }

    @Test
    @DisplayName("状态查询：网络不可达 -> null（监控任务据此走重试/超限）")
    void 状态查询_网络失败() {
        stubRecordExists();
        properties.setSimBaseUrl(UNREACHABLE_URL);

        assertThat(service.queryState(DEVICE_NO)).isNull();
    }
}
