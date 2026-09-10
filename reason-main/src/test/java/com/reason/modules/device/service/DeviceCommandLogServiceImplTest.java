package com.reason.modules.device.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reason.common.exception.RRException;
import com.reason.common.utils.PageUtils;
import com.reason.modules.device.dao.DeviceCommandLogDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.enums.CommandStatus;
import com.reason.modules.device.enums.TriggerType;
import com.reason.modules.device.form.DeviceCommandLogForm;
import com.reason.modules.device.service.impl.DeviceCommandLogServiceImpl;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指令流水 seq 启动对齐测试（批次4）：Redis 缺失/落后（旧 dump 回退）-> LUA 对齐取大 DB MAX；
 * 对齐失败不阻断启动（尽力恢复）——复现批次3 实测事故的预防侧
 */
@DisplayName("指令流水(批次4 seq 对齐)")
@ExtendWith(MockitoExtension.class)
class DeviceCommandLogServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DeviceCommandLogDao deviceCommandLogDao;
    @InjectMocks
    private DeviceCommandLogServiceImpl commandLogService;

    /** 纯单测无 MyBatis 装配：Lambda 包装器依赖 TableInfo 缓存（A2 补网用例需要） */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeviceCommandLogEntity.class);
    }

    @BeforeEach
    void injectBaseMapper() {
        //ServiceImpl 父类 baseMapper 字段（@InjectMocks 覆盖不到父类泛型字段）
        ReflectionTestUtils.setField(commandLogService, "baseMapper", deviceCommandLogDao);
    }

    @Test
    @DisplayName("启动对齐：DB MAX=11 / Redis 落后或缺失 -> LUA 校正（返回 1）")
    void 对齐_落后校正() {
        when(deviceCommandLogDao.selectMaps(any()))
                .thenReturn(List.of(Map.of("device_no", "BARRIER-B-01", "max_seq", 11L)));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        commandLogService.seedSeqFromDb();

        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), eq("11"));
    }

    @Test
    @DisplayName("启动对齐：Redis 现值领先（返回 0，不做覆盖）——判定已发生且不炸")
    void 对齐_领先不动() {
        when(deviceCommandLogDao.selectMaps(any()))
                .thenReturn(List.of(Map.of("device_no", "BARRIER-B-01", "max_seq", 11L)));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(0L);

        commandLogService.seedSeqFromDb();

        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), eq("11"));
    }

    @Test
    @DisplayName("对齐失败（Redis 抖动）：不抛异常不阻断启动（尽力恢复而非启动强依赖）")
    void 对齐_异常不阻断启动() {
        when(deviceCommandLogDao.selectMaps(any()))
                .thenReturn(List.of(Map.of("device_no", "BARRIER-B-01", "max_seq", 11L)));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        //不抛 = 通过（catch 全方法兜底）
        commandLogService.seedSeqFromDb();
    }

    // ---------- A2 补网：seq 分配与流水状态机全分支 ----------

    private DeviceCommandLogEntity entity(long seq, int status) {
        DeviceCommandLogEntity entity = new DeviceCommandLogEntity();
        entity.setDeviceNo("BARRIER-B-01");
        entity.setCommandAction("OPEN");
        entity.setCommandSeq(seq);
        entity.setCommandStatus(status);
        return entity;
    }

    @Test
    @DisplayName("seq 分配：Redis INCR 原子递增")
    void seq分配_正常() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(5L);

        assertThat(commandLogService.nextSeq("BARRIER-B-01")).isEqualTo(5L);
    }

    @Test
    @DisplayName("seq 分配：Redis 不可用 -> 快速失败（宁可不发不能发重）")
    void seq分配_Redis不可用() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(null);

        assertThatThrownBy(() -> commandLogService.nextSeq("BARRIER-B-01"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("指令序号生成失败");
    }

    @Test
    @DisplayName("记流水：PENDING 初态 + 触发类型 + 毫秒时间戳落库")
    void 记流水_字段正确() {
        when(deviceCommandLogDao.insert(any(DeviceCommandLogEntity.class))).thenReturn(1);

        DeviceCommandLogEntity saved = commandLogService.recordPending("BARRIER-B-01", "OPEN", 7L, TriggerType.MANUAL);

        assertThat(saved.getDeviceNo()).isEqualTo("BARRIER-B-01");
        assertThat(saved.getCommandStatus()).isEqualTo(CommandStatus.PENDING.getCode());
        assertThat(saved.getTriggerType()).isEqualTo(TriggerType.MANUAL.getCode());
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getCommandCreatetime()).isEqualTo(saved.getCommandUpdatetime()).isPositive();
        verify(deviceCommandLogDao).insert(any(DeviceCommandLogEntity.class));
    }

    @Test
    @DisplayName("PENDING 计数：指标端点用的积压观测")
    void 计数_待到位() {
        when(deviceCommandLogDao.selectCount(any())).thenReturn(7L);

        assertThat(commandLogService.countPending()).isEqualTo(7);
    }

    @Test
    @DisplayName("CAS 推进：markSendFailed 条件更新（PENDING 守卫在 SQL 层）")
    void CAS_发送失败() {
        when(deviceCommandLogDao.update(isNull(), any())).thenReturn(1);

        commandLogService.markSendFailed(5L);

        verify(deviceCommandLogDao).update(isNull(), any());
    }

    @Test
    @DisplayName("按 seq 精确销账：命中（true）——事件到达推进 ARRIVED")
    void 销账_命中() {
        when(deviceCommandLogDao.update(isNull(), any())).thenReturn(1);

        assertThat(commandLogService.markArrivedBySeq("BARRIER-B-01", 7L, "OPEN")).isTrue();
    }

    @Test
    @DisplayName("按 seq 精确销账：未命中（false）——已闭环/已终态/非指令驱动（幂等）")
    void 销账_未命中() {
        when(deviceCommandLogDao.update(isNull(), any())).thenReturn(0);

        assertThat(commandLogService.markArrivedBySeq("BARRIER-B-01", 7L, "OPEN")).isFalse();
    }

    @Test
    @DisplayName("故障中断：按 seq 归属中断（0.6 精确中断）")
    void 故障中断_按seq() {
        when(deviceCommandLogDao.update(isNull(), any())).thenReturn(1);

        assertThat(commandLogService.markExecFailedBySeq("BARRIER-B-01", 7L)).isTrue();
    }

    @Test
    @DisplayName("重试计数：条件自增（已终态不再计数）")
    void 重试计数() {
        when(deviceCommandLogDao.update(isNull(), any())).thenReturn(1);

        commandLogService.markRetried(5L);

        verify(deviceCommandLogDao).update(isNull(), any());
    }

    @Test
    @DisplayName("达上限定格 RETRY_EXCEEDED：CAS 命中 true / 未命中 false（返回值透传给告警决策）")
    void 超限定格() {
        when(deviceCommandLogDao.update(isNull(), any())).thenReturn(1, 0);

        assertThat(commandLogService.markRetryExceeded(5L)).isTrue();
        assertThat(commandLogService.markRetryExceeded(6L)).isFalse();
    }

    @Test
    @DisplayName("代际取代 SUPERSEDED：CAS 透传")
    void 被取代定格() {
        when(deviceCommandLogDao.update(isNull(), any())).thenReturn(1);

        assertThat(commandLogService.markSuperseded(5L)).isTrue();
    }

    @Test
    @DisplayName("超时扫描：返回在途超期流水（监控任务入口数据源）")
    void 超时扫描() {
        DeviceCommandLogEntity pending = entity(7L, CommandStatus.PENDING.getCode());
        when(deviceCommandLogDao.selectList(any())).thenReturn(List.of(pending));

        assertThat(commandLogService.findTimeoutPending(30)).containsExactly(pending);
    }

    @Test
    @DisplayName("管理端分页：参数钳制后查询 + 结果包装（T15）")
    @SuppressWarnings("rawtypes")
    void 分页查询() {
        Page<DeviceCommandLogEntity> resultPage = new Page<>(2, 20);
        resultPage.setRecords(List.of(entity(7L, CommandStatus.ARRIVED.getCode())));
        resultPage.setTotal(1);
        when(deviceCommandLogDao.selectPage(any(), any())).thenReturn(resultPage);

        DeviceCommandLogForm form = new DeviceCommandLogForm();
        form.setPage("2");
        form.setLimit("20");
        PageUtils pageUtils = commandLogService.queryPage(form);

        ArgumentCaptor<IPage> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(deviceCommandLogDao).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
        assertThat(pageUtils).isNotNull();
    }
}
