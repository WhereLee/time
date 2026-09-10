package com.reason.modules.device.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 设备通道鉴权单测（A2 补网）：per-device HMAC 验签入口（事件/心跳两个 canonical 域）——
 * 正签名放行；未登记/无密钥 fail secure（401）；伪造/空签名 401。
 * 签名用真实 {@link DeviceSignature} 计算（与双端契约同一算法），非 mock。
 */
@DisplayName("设备通道鉴权(per-device HMAC)")
@ExtendWith(MockitoExtension.class)
class DeviceChannelAuthenticatorTest {

    private static final String DEVICE_NO = "BARRIER-E-01";
    private static final String SECRET = "aabbccddeeff00112233445566778899";

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

    private DeviceChannelAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new DeviceChannelAuthenticator(recordDao);
    }

    private DeviceRecordEntity record(String secret) {
        DeviceRecordEntity record = new DeviceRecordEntity();
        record.setDeviceNo(DEVICE_NO);
        record.setDeviceSecret(secret);
        return record;
    }

    /** 统一断言：401 + 原因文本 */
    private static void assertUnauthorized(Runnable call, String messagePart) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(messagePart)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(401));
    }

    // ---------- 事件通道 ----------

    @Test
    @DisplayName("事件：正签名放行（含 commandSeq 的完整 canonical）")
    void 事件_正签名放行() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));
        String sig = DeviceSignature.sign(SECRET,
                DeviceSignature.canonicalEvent(DEVICE_NO, 1, 7L, "boot-1", 42L));

        assertThatCode(() -> authenticator.authenticateEvent(DEVICE_NO, 1, 7L, "boot-1", 42L, sig))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("事件：commandSeq 为空的 canonical（手动外力事件）正签名放行")
    void 事件_无指令序号正签名放行() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));
        String sig = DeviceSignature.sign(SECRET,
                DeviceSignature.canonicalEvent(DEVICE_NO, 0, null, "boot-1", 43L));

        assertThatCode(() -> authenticator.authenticateEvent(DEVICE_NO, 0, null, "boot-1", 43L, sig))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("事件：设备未登记（查无档案）-> 401 fail secure")
    void 事件_未登记401() {
        when(recordDao.selectOne(any())).thenReturn(null);

        assertUnauthorized(() -> authenticator.authenticateEvent(DEVICE_NO, 1, 7L, "boot-1", 42L, "any"),
                "设备未登记");
    }

    @Test
    @DisplayName("事件：档案存在但密钥为空 -> 401 fail secure（未配置密钥=未登记）")
    void 事件_密钥空401() {
        when(recordDao.selectOne(any())).thenReturn(record(""));

        assertUnauthorized(() -> authenticator.authenticateEvent(DEVICE_NO, 1, 7L, "boot-1", 42L, "any"),
                "设备未登记");
    }

    @Test
    @DisplayName("事件：伪造签名 -> 401 签名无效")
    void 事件_伪造签名401() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));

        assertUnauthorized(() -> authenticator.authenticateEvent(DEVICE_NO, 1, 7L, "boot-1", 42L, "deadbeef"),
                "签名无效");
    }

    @Test
    @DisplayName("事件：空签名 -> 401 签名无效（verify 前置短路）")
    void 事件_空签名401() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));

        assertUnauthorized(() -> authenticator.authenticateEvent(DEVICE_NO, 1, 7L, "boot-1", 42L, ""),
                "签名无效");
    }

    @Test
    @DisplayName("事件：串域签名（用心跳 canonical 签事件）-> 401（域隔离）")
    void 事件_跨域签名401() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));
        String heartbeatSig = DeviceSignature.sign(SECRET,
                DeviceSignature.canonicalHeartbeat(DEVICE_NO, 1));

        assertUnauthorized(() -> authenticator.authenticateEvent(DEVICE_NO, 1, 7L, "boot-1", 42L, heartbeatSig),
                "签名无效");
    }

    // ---------- 心跳通道 ----------

    @Test
    @DisplayName("心跳：带状态正签名放行")
    void 心跳_带状态正签名放行() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));
        String sig = DeviceSignature.sign(SECRET, DeviceSignature.canonicalHeartbeat(DEVICE_NO, 1));

        assertThatCode(() -> authenticator.authenticateHeartbeat(DEVICE_NO, 1, sig))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("心跳：state 为空（只报活）正签名放行——canonical 空串分支")
    void 心跳_无状态正签名放行() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));
        String sig = DeviceSignature.sign(SECRET, DeviceSignature.canonicalHeartbeat(DEVICE_NO, null));

        assertThatCode(() -> authenticator.authenticateHeartbeat(DEVICE_NO, null, sig))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("心跳：伪造签名 -> 401")
    void 心跳_伪造签名401() {
        when(recordDao.selectOne(any())).thenReturn(record(SECRET));

        assertUnauthorized(() -> authenticator.authenticateHeartbeat(DEVICE_NO, 1, "bad"), "签名无效");
    }

    @Test
    @DisplayName("心跳：未登记 -> 401 fail secure")
    void 心跳_未登记401() {
        when(recordDao.selectOne(any())).thenReturn(null);

        assertUnauthorized(() -> authenticator.authenticateHeartbeat(DEVICE_NO, 1, "any"), "设备未登记");
    }
}
