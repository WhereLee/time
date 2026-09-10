package com.reason.modules.device.service;

import com.reason.common.exception.RRException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.impl.DeviceMonitorServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备心跳处理编排测试（阶段1 实测暴露补漏后补钉）：
 * 心跳到达 = 离线判定反转的权威（自动关闭 OFFLINE 告警）；对账漂移才告警（首次接入豁免）；
 * FAULT 心跳与事件通道同收口（中断在途）；未登记设备心跳显式拒绝
 */
@DisplayName("设备心跳编排(阶段1补漏)")
@ExtendWith(MockitoExtension.class)
class DeviceMonitorServiceImplTest {

    /**
     * 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存（见
     * document/pitfalls/mybatis-plus-lambda-cache-unit-test.md——覆盖实现内引用到的全部实体）
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeviceRecordEntity.class);
    }

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private BarrierProperties barrierProperties;
    @Mock
    private DeviceRecordDao recordDao;
    @Mock
    private DeviceRecordService deviceRecordService;
    @Mock
    private DeviceAlarmService deviceAlarmService;
    @Mock
    private DeviceCommandLogService commandLogService;
    @InjectMocks
    private DeviceMonitorServiceImpl monitorService;

    private DeviceRecordEntity record(int state) {
        DeviceRecordEntity record = new DeviceRecordEntity();
        record.setDeviceNo("BARRIER-E-01");
        record.setDeviceState(state);
        record.setDeviceUpdatetime(System.currentTimeMillis() - 60_000); //远离 grace 窗（D-H：毫秒列）
        return record;
    }

    private void stubRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(barrierProperties.getHeartbeatTimeoutSeconds()).thenReturn(30);
    }

    @Test
    @DisplayName("心跳稳定一致：关闭 OFFLINE 告警（恢复权威=心跳到达）+ 续在线 key")
    void heartbeat一致_关离线告警续key() {
        when(recordDao.selectOne(any())).thenReturn(record(DeviceState.DOWN.getCode()));
        stubRedis();
        monitorService.heartbeat("BARRIER-E-01", DeviceState.DOWN.getCode());
        verify(deviceAlarmService).markOnlineRecovered("BARRIER-E-01");
        verify(valueOperations).set(eq("barrier:online:BARRIER-E-01"), anyString(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("心跳状态漂移（稳定态不一致且非首次）：校正台账 + STATE_MISMATCH 告警，仍关离线告警")
    void heartbeat漂移_校正并告警() {
        when(recordDao.selectOne(any())).thenReturn(record(DeviceState.DOWN.getCode()));
        stubRedis();
        monitorService.heartbeat("BARRIER-E-01", DeviceState.UP.getCode());
        verify(deviceRecordService).updateStateByEvent("BARRIER-E-01", DeviceState.UP.getCode());
        verify(deviceAlarmService).raise(eq("BARRIER-E-01"), eq(AlarmType.STATE_MISMATCH), anyString());
        verify(deviceAlarmService).markOnlineRecovered("BARRIER-E-01");
    }

    @Test
    @DisplayName("首次接入（台账未接入态）：只校正不告警 STATE_MISMATCH")
    void heartbeat首次接入_只校正不告警() {
        when(recordDao.selectOne(any())).thenReturn(record(DeviceState.NOT_CONNECTED.getCode()));
        stubRedis();
        monitorService.heartbeat("BARRIER-E-01", DeviceState.DOWN.getCode());
        verify(deviceRecordService).updateStateByEvent("BARRIER-E-01", DeviceState.DOWN.getCode());
        verify(deviceAlarmService, never()).raise(anyString(), eq(AlarmType.STATE_MISMATCH), anyString());
    }

    @Test
    @DisplayName("心跳自述 FAULT（0.6 双通道收口）：告警 + 中断在途 + 仍关离线告警")
    void heartbeatFault_收口与告警() {
        when(recordDao.selectOne(any())).thenReturn(record(DeviceState.UP.getCode()));
        stubRedis();
        monitorService.heartbeat("BARRIER-E-01", DeviceState.FAULT.getCode());
        verify(deviceAlarmService).raise(eq("BARRIER-E-01"), eq(AlarmType.DEVICE_FAULT), anyString());
        verify(commandLogService).markExecFailed("BARRIER-E-01");
        verify(deviceAlarmService).markOnlineRecovered("BARRIER-E-01");
    }

    @Test
    @DisplayName("未登记设备心跳：显式拒绝（配置错位立即可见）")
    void heartbeat未登记_显式失败() {
        when(recordDao.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> monitorService.heartbeat("BARRIER-UNKNOWN", DeviceState.DOWN.getCode()))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("未登记");
        verify(deviceAlarmService, never()).markOnlineRecovered(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("scanOffline（批次2 B6）：pipeline 批量判定一次往返——仅离线集 raise，在线零误报")
    void scanOffline_pipeline批量判定() {
        when(barrierProperties.getOnlineStartupGraceSeconds()).thenReturn(90);
        //批次4：单测 mock 的 int 默认 0 会把任何离线数判为批量态——显式给阈值
        when(barrierProperties.getOfflineBatchThreshold()).thenReturn(3);
        //两台接入过设备（未接入过滤是查询 SQL 职责，单测不模拟）：E-01 心跳 key 过期（离线）、W-02 在线
        DeviceRecordEntity offline = record(DeviceState.UP.getCode());
        offline.setDeviceNo("BARRIER-E-01");
        DeviceRecordEntity online = record(DeviceState.UP.getCode());
        online.setDeviceNo("BARRIER-W-02");
        when(recordDao.selectList(any())).thenReturn(List.of(offline, online));
        //pipeline 返回逐 key EXISTS 结果（E-01 过期=false，W-02=true）；匹配器必须内联（变量传递会被当字面量）
        when(stringRedisTemplate.executePipelined(any(RedisCallback.class)))
                .thenReturn(List.of(Boolean.FALSE, Boolean.TRUE));

        monitorService.scanOffline();

        //离线集=E-01 一条：raise OFFLINE；在线（W-02）零 raise（无误报）
        verify(deviceAlarmService).raise(eq("BARRIER-E-01"), eq(AlarmType.OFFLINE), anyString());
        verify(deviceAlarmService, never()).raise(eq("BARRIER-W-02"), eq(AlarmType.OFFLINE), anyString());
        //批量判定经 pipeline 单次往返（不再逐台 hasKey）
        verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
    }

    @Test
    @DisplayName("scanOffline：无接入过设备（空集）→ 跳过扫描零 Redis 往返")
    void scanOffline_空集跳过() {
        when(barrierProperties.getOnlineStartupGraceSeconds()).thenReturn(90);
        when(recordDao.selectList(any())).thenReturn(List.of());

        monitorService.scanOffline();

        verify(stringRedisTemplate, never()).executePipelined(any(RedisCallback.class));
        verify(deviceAlarmService, never()).raise(anyString(), eq(AlarmType.OFFLINE), anyString());
    }

    @Test
    @DisplayName("scanOffline：启动宽限期内跳过（平台重启恢复不误报全量离线）")
    void scanOffline_启动宽限跳过() {
        when(barrierProperties.getOnlineStartupGraceSeconds()).thenReturn(Integer.MAX_VALUE);
        monitorService.scanOffline();
        verify(recordDao, never()).selectList(any());
        verify(stringRedisTemplate, never()).executePipelined(any(RedisCallback.class));
    }

    @Test
    @DisplayName("scanOffline（批次4 D-F）：离线数>=阈值 -> 合并 1 条 BATCH_OFFLINE，不逐台 raise、不触关闭")
    void scanOffline_批量合并() {
        when(barrierProperties.getOnlineStartupGraceSeconds()).thenReturn(90);
        when(barrierProperties.getOfflineBatchThreshold()).thenReturn(3);
        DeviceRecordEntity d1 = record(DeviceState.UP.getCode());
        d1.setDeviceNo("BARRIER-B-01");
        DeviceRecordEntity d2 = record(DeviceState.UP.getCode());
        d2.setDeviceNo("BARRIER-B-02");
        DeviceRecordEntity d3 = record(DeviceState.UP.getCode());
        d3.setDeviceNo("BARRIER-B-03");
        when(recordDao.selectList(any())).thenReturn(List.of(d1, d2, d3));
        when(stringRedisTemplate.executePipelined(any(RedisCallback.class)))
                .thenReturn(List.of(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE));

        monitorService.scanOffline();

        //合并一条（哨兵号 + 批量类型）；无逐台 OFFLINE、无批量态关闭（本轮仍是批量态）
        verify(deviceAlarmService).raise(eq(DeviceAlarmService.BATCH_DEVICE_NO), eq(AlarmType.BATCH_OFFLINE), anyString());
        verify(deviceAlarmService, never()).raise(anyString(), eq(AlarmType.OFFLINE), anyString());
        verify(deviceAlarmService, never()).closeUnhandledAlarm(anyString(), any());
    }

    @Test
    @DisplayName("scanOffline（批次4 D-F）：离线数回落低于阈值 -> 逐台 raise + 关闭批量告警（谁开谁关）")
    void scanOffline_回落逐台并关批量() {
        when(barrierProperties.getOnlineStartupGraceSeconds()).thenReturn(90);
        when(barrierProperties.getOfflineBatchThreshold()).thenReturn(3);
        when(barrierProperties.getHeartbeatTimeoutSeconds()).thenReturn(30);
        DeviceRecordEntity d1 = record(DeviceState.UP.getCode());
        d1.setDeviceNo("BARRIER-B-01");
        DeviceRecordEntity d2 = record(DeviceState.UP.getCode());
        d2.setDeviceNo("BARRIER-B-02");
        when(recordDao.selectList(any())).thenReturn(List.of(d1, d2));
        when(stringRedisTemplate.executePipelined(any(RedisCallback.class)))
                .thenReturn(List.of(Boolean.FALSE, Boolean.FALSE));

        monitorService.scanOffline();

        verify(deviceAlarmService).closeUnhandledAlarm(eq(DeviceAlarmService.BATCH_DEVICE_NO), eq(AlarmType.BATCH_OFFLINE));
        verify(deviceAlarmService).raise(eq("BARRIER-B-01"), eq(AlarmType.OFFLINE), anyString());
        verify(deviceAlarmService).raise(eq("BARRIER-B-02"), eq(AlarmType.OFFLINE), anyString());
    }
}
