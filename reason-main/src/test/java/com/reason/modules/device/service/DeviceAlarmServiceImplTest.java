package com.reason.modules.device.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceAlarmDao;
import com.reason.modules.device.entity.DeviceAlarmEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.service.impl.DeviceAlarmServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备告警服务测试（批次2，F2——B5 OFFLINE 标记门控）：
 * raise 落库成功置标记/去重跳过不置/落库异常回滚占位不置；markOnlineRecovered
 * GETDEL 命中才 UPDATE / 未命中零 DB 写 / Redis 异常降级为直接 UPDATE（防漏关）
 *
 * <p>ServiceImpl 父类 baseMapper 无法被 @InjectMocks 注入（纯单测限制，
 * 见 document/pitfalls/mybatis-plus-injectmocks-basermapper-unit-test.md——
 * 该文件为遗留 this/baseMapper 风格，按最小注入处理，不扩大改造面）。</p>
 */
@DisplayName("设备告警(批次2标记门控)")
@ExtendWith(MockitoExtension.class)
class DeviceAlarmServiceImplTest {

    private static final String DEVICE_NO = "BARRIER-E-01";
    private static final String OPEN_KEY =
            BarrierRedisKeys.ALARM_OPEN_PREFIX + DEVICE_NO + ":" + AlarmType.OFFLINE.getCode();

    /**
     * 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存（SerializedLambda 反解列名）——
     * 手动初始化被测实体（见 document/pitfalls/mybatis-plus-lambda-cache-unit-test.md）
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeviceAlarmEntity.class);
    }

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private BarrierProperties barrierProperties;
    @Mock
    private DeviceAlarmDao deviceAlarmDao;
    @InjectMocks
    private DeviceAlarmServiceImpl alarmService;

    @BeforeEach
    void injectBaseMapper() {
        //ServiceImpl 父类 baseMapper 字段（@InjectMocks 覆盖不到父类泛型字段）
        ReflectionTestUtils.setField(alarmService, "baseMapper", deviceAlarmDao);
    }

    private void stubRedisOps() {
        //仅 Redis 交互用例需要（handle 用例不触 Redis——放用例内避免 UnnecessaryStubbing）
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void stubDedupSeconds() {
        //去重窗口秒数仅在 raise 流程消费（去重跳过等用例不触达——放用例内避免 UnnecessaryStubbing）
        when(barrierProperties.getAlarmDedupSeconds()).thenReturn(300);
    }

    @Test
    @DisplayName("raise OFFLINE 落库成功：SET 未处理标记（无 TTL，待恢复路径 GETDEL 门控）")
    void raise离线_落库后置标记() {
        stubRedisOps();
        stubDedupSeconds();
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(300L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceAlarmDao.selectCount(any())).thenReturn(0L);

        alarmService.raise(DEVICE_NO, AlarmType.OFFLINE, "心跳超时(>30s)未收到，判定离线");

        //落库（insert）+ 标记（SET）都发生；标记 key 带设备号与类型码
        verify(deviceAlarmDao).insert(any(DeviceAlarmEntity.class));
        verify(valueOperations).set(eq(OPEN_KEY), eq("1"));
    }

    @Test
    @DisplayName("raise 去重窗口内（SETNX false）：跳过落库，不置标记")
    void raise离线_去重跳过不置标记() {
        stubRedisOps();
        stubDedupSeconds();
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(300L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        alarmService.raise(DEVICE_NO, AlarmType.OFFLINE, "心跳超时");

        verify(deviceAlarmDao, never()).insert(any(DeviceAlarmEntity.class));
        verify(valueOperations, never()).set(eq(OPEN_KEY), anyString());
    }

    @Test
    @DisplayName("raise 落库异常：回滚去重占位 + 上抛，不置标记（占位空窗不可接受）")
    void raise离线_落库异常回滚占位() {
        stubRedisOps();
        stubDedupSeconds();
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(300L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceAlarmDao.selectCount(any())).thenReturn(0L);
        when(deviceAlarmDao.insert(any(DeviceAlarmEntity.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> alarmService.raise(DEVICE_NO, AlarmType.OFFLINE, "心跳超时"))
                .isInstanceOf(RuntimeException.class);
        //占位回滚是 template 级 delete（raise 实现内 stringRedisTemplate.delete）
        verify(stringRedisTemplate).delete(anyString());
        verify(valueOperations, never()).set(eq(OPEN_KEY), anyString());
    }

    @Test
    @DisplayName("markOnlineRecovered：原子取删命中（该设备离线过）→ 执行关闭 UPDATE")
    void 恢复_标记命中执行update() {
        //取删走 LUA 脚本（Redis 5.0 无 GETDEL 命令，脚本兼容保持原子——见实现类 GETDEL_SCRIPT 注释）；
        //execute 是 varargs 方法：实现侧无 args 调用（空数组），stub 须省略第三参才匹配
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn("1");
        when(deviceAlarmDao.update(isNull(), any())).thenReturn(1);

        int rows = alarmService.markOnlineRecovered(DEVICE_NO);

        assertThat(rows).isEqualTo(1);
        verify(deviceAlarmDao).update(isNull(), any());
    }

    @Test
    @DisplayName("markOnlineRecovered：取删未命中（从未离线告警）→ 零 DB 写（心跳洪峰写放大归零）")
    void 恢复_未命中零db写() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(null);

        int rows = alarmService.markOnlineRecovered(DEVICE_NO);

        assertThat(rows).isZero();
        verify(deviceAlarmDao, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("markOnlineRecovered：取删异常（Redis 故障）→ 降级按命中处理执行 UPDATE（防漏关）")
    void 恢复_getdel异常降级update() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList()))
                .thenThrow(new RuntimeException("redis down"));
        when(deviceAlarmDao.update(isNull(), any())).thenReturn(1);

        int rows = alarmService.markOnlineRecovered(DEVICE_NO);

        assertThat(rows).isEqualTo(1);
        verify(deviceAlarmDao).update(isNull(), any());
    }

    @Test
    @DisplayName("人工 handle 不删标记（恢复路径 GETDEL 一次性吸收，幂等无害）——handle 无 Redis 交互")
    void handle_不触碰标记() {
        when(deviceAlarmDao.update(any(), any())).thenReturn(1);

        alarmService.handle(42L, 1L);

        verify(stringRedisTemplate, never()).delete(anyString());
        verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList());
    }
}
