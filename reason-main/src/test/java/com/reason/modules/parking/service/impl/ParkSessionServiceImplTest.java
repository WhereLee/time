package com.reason.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.common.exception.RRException;
import com.reason.modules.parking.dao.FeeRuleDao;
import com.reason.modules.parking.dao.ParkOrderDao;
import com.reason.modules.parking.dao.ParkSessionDao;
import com.reason.modules.parking.dao.ParkSpaceDao;
import com.reason.modules.parking.entity.FeeRuleEntity;
import com.reason.modules.parking.entity.ParkOrderEntity;
import com.reason.modules.parking.entity.ParkSessionEntity;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.enums.FeeRuleState;
import com.reason.modules.parking.enums.ParkSessionState;
import com.reason.modules.parking.enums.ParkSpaceState;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 停车会话服务单元测试（入场/取消事务矩阵）
 *
 * <p>并发正确性分层验证：本层 mock 验证「行数 0 → 拒绝」等分支逻辑；
 * 真实并发（仅一条条件更新生效）由 ParkSessionIT 在 CI 容器内验证。</p>
 */
@DisplayName("停车会话服务")
@ExtendWith(MockitoExtension.class)
class ParkSessionServiceImplTest {

    private static final long SPACE_ID = 1L;
    private static final String SPACE_NO = "A-001";

    @Mock
    private ParkSpaceDao parkSpaceDao;
    @Mock
    private ParkSessionDao parkSessionDao;
    @Mock
    private FeeRuleDao feeRuleDao;
    @Mock
    private ParkOrderDao parkOrderDao;
    @InjectMocks
    private ParkSessionServiceImpl parkSessionService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        //纯单测无 Spring 上下文：Lambda 包装器（LambdaQueryWrapper/LambdaUpdateWrapper）
        //依赖 MP TableInfo 元数据缓存解析方法引用，需手动初始化目标实体
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ParkSpaceEntity.class);
        TableInfoHelper.initTableInfo(assistant, ParkSessionEntity.class);
        TableInfoHelper.initTableInfo(assistant, FeeRuleEntity.class);
        TableInfoHelper.initTableInfo(assistant, ParkOrderEntity.class);
    }

    // ---------- 入场 ----------

    @Test
    @DisplayName("入场成功：车位占用 + 创建进行中会话（编号/车牌规范化大写）")
    void 入场成功() {
        when(parkSpaceDao.selectOne(any())).thenReturn(idleSpace());
        when(parkSessionDao.selectCount(any())).thenReturn(0L);
        when(parkSpaceDao.update(isNull(), any())).thenReturn(1);
        //MP 自增主键回填由真实 insert 完成，mock 需模拟回填
        doAnswer(inv -> {
            ParkSessionEntity e = inv.getArgument(0);
            e.setSessionId(100L);
            return 1;
        }).when(parkSessionDao).insert(any(ParkSessionEntity.class));

        Long sessionId = parkSessionService.entry("  a-001 ", " 浙b12345 ");

        assertThat(sessionId).isEqualTo(100L);
        ArgumentCaptor<ParkSessionEntity> captor = ArgumentCaptor.forClass(ParkSessionEntity.class);
        verify(parkSessionDao).insert(captor.capture());
        ParkSessionEntity inserted = captor.getValue();
        assertThat(inserted.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(inserted.getSpaceNo()).isEqualTo(SPACE_NO);       //冗余自车位记录
        assertThat(inserted.getPlateNo()).isEqualTo("浙B12345");      //trim + 大写
        assertThat(inserted.getSessionState()).isEqualTo(ParkSessionState.ONGOING.getCode());
        assertThat(inserted.getSessionEntryTime()).isNotNull();
    }

    @Test
    @DisplayName("入场：车位不存在 → 业务异常")
    void 入场车位不存在() {
        when(parkSpaceDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> parkSessionService.entry(SPACE_NO, "浙B12345"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位不存在");
            }

    @Test
    @DisplayName("入场：车位已禁用 → 业务异常")
    void 入场车位禁用() {
        when(parkSpaceDao.selectOne(any())).thenReturn(spaceWithState(ParkSpaceState.DISABLED));

        assertThatThrownBy(() -> parkSessionService.entry(SPACE_NO, "浙B12345"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已禁用");
    }

    @Test
    @DisplayName("入场：车位存在进行中会话（查重兜底命中）→ 拒绝且不再发起占位更新")
    void 入场查重兜底拒绝() {
        when(parkSpaceDao.selectOne(any())).thenReturn(idleSpace());
        when(parkSessionDao.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> parkSessionService.entry(SPACE_NO, "浙B12345"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已被占用");
        verify(parkSpaceDao, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("入场：条件更新 0 行（并发窗口内被抢先）→ 拒绝且不创建会话")
    void 入场并发被占拒绝() {
        when(parkSpaceDao.selectOne(any())).thenReturn(idleSpace());
        when(parkSessionDao.selectCount(any())).thenReturn(0L);
        when(parkSpaceDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> parkSessionService.entry(SPACE_NO, "浙B12345"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已被占用");
            }

    @Test
    @DisplayName("入场：空编号/空车牌 → 参数校验异常")
    void 入场空参防御() {
        assertThatThrownBy(() -> parkSessionService.entry(null, "浙B12345"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位编号");
        assertThatThrownBy(() -> parkSessionService.entry(SPACE_NO, "  "))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车牌号");
    }

    // ---------- 取消 ----------

    @Test
    @DisplayName("取消成功：会话终态化 + 车位释放（占用 → 空闲）")
    void 取消成功() {
        when(parkSessionDao.selectById(100L)).thenReturn(ongoingSession());
        when(parkSessionDao.update(isNull(), any())).thenReturn(1);
        when(parkSpaceDao.update(isNull(), any())).thenReturn(1);

        parkSessionService.cancel(100L, "误入场");

        //行为级验证：会话终态化 + 车位释放各一次；条件更新的 WHERE 语义（仅进行中可终态化）由 ParkSessionIT 真库并发验证
        verify(parkSessionDao).update(isNull(), any());
        verify(parkSpaceDao).update(isNull(), any());
    }

    @Test
    @DisplayName("取消：会话不存在 → 业务异常")
    void 取消会话不存在() {
        when(parkSessionDao.selectById(100L)).thenReturn(null);

        assertThatThrownBy(() -> parkSessionService.cancel(100L, "x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("取消：已结束会话（终态不可逆）→ 守卫拒绝")
    void 取消已结束会话拒绝() {
        when(parkSessionDao.selectById(100L)).thenReturn(sessionWithState(ParkSessionState.FINISHED));

        assertThatThrownBy(() -> parkSessionService.cancel(100L, "x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法的会话状态迁移");
        verify(parkSessionDao, never()).update(isNull(), any());
        verify(parkSpaceDao, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("取消：已取消会话 → 守卫拒绝")
    void 取消已取消会话拒绝() {
        when(parkSessionDao.selectById(100L)).thenReturn(sessionWithState(ParkSessionState.CANCELLED));

        assertThatThrownBy(() -> parkSessionService.cancel(100L, "x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法的会话状态迁移");
    }

    @Test
    @DisplayName("取消：会话终态化条件更新 0 行（并发下已被其他路径终态化）→ 拒绝且不释放车位")
    void 取消并发已变更拒绝() {
        when(parkSessionDao.selectById(100L)).thenReturn(ongoingSession());
        when(parkSessionDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> parkSessionService.cancel(100L, "x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("状态已变更");
        verify(parkSpaceDao, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("取消：车位释放 0 行（会话与车位状态不一致的数据异常）→ 回滚暴露而非静默")
    void 取消释放失败回滚() {
        when(parkSessionDao.selectById(100L)).thenReturn(ongoingSession());
        when(parkSessionDao.update(isNull(), any())).thenReturn(1);
        when(parkSpaceDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> parkSessionService.cancel(100L, "x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位状态异常");
    }

    @Test
    @DisplayName("取消：会话 id 为空 → 参数校验异常")
    void 取消空参防御() {
        assertThatThrownBy(() -> parkSessionService.cancel(null, "x"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("会话id");
    }

    // ---------- 出场结算 ----------

    @Test
    @DisplayName("出场成功：会话终态化 + 订单快照（算费/单价快照/冗余字段）+ 车位释放")
    void 出场成功() {
        //入场已 3700 秒（1 小时 1 分 40 秒）→ 时长 62 分钟、按 2 小时计费 400 分
        when(parkSessionDao.selectById(100L)).thenReturn(sessionParkedSecondsAgo(3700));
        when(parkSessionDao.update(isNull(), any())).thenReturn(1);
        when(feeRuleDao.selectList(any())).thenReturn(java.util.List.of(enabledRule(200)));
        when(parkSpaceDao.update(isNull(), any())).thenReturn(1);
        doAnswer(inv -> {
            ParkOrderEntity o = inv.getArgument(0);
            o.setOrderId(200L);
            return 1;
        }).when(parkOrderDao).insert(any(ParkOrderEntity.class));

        Long orderId = parkSessionService.exit(100L);

        assertThat(orderId).isEqualTo(200L);
        ArgumentCaptor<ParkOrderEntity> captor = ArgumentCaptor.forClass(ParkOrderEntity.class);
        verify(parkOrderDao).insert(captor.capture());
        ParkOrderEntity order = captor.getValue();
        assertThat(order.getSessionId()).isEqualTo(100L);
        assertThat(order.getPlateNo()).isEqualTo("浙B12345");            //快照自会话
        assertThat(order.getSpaceNo()).isEqualTo(SPACE_NO);              //快照自会话
        assertThat(order.getOrderExitTime()).isNotNull();
        assertThat(order.getDurationMinutes()).isEqualTo(62);            //ceil(3700/60)
        assertThat(order.getUnitPriceFen()).isEqualTo(200);              //单价快照自规则
        assertThat(order.getAmountFen()).isEqualTo(400L);                //ceil(3700/3600)=2h × 200
        assertThat(order.getOrderState()).isEqualTo(0);
    }

    @Test
    @DisplayName("出场：会话不存在 → 业务异常")
    void 出场会话不存在() {
        when(parkSessionDao.selectById(100L)).thenReturn(null);

        assertThatThrownBy(() -> parkSessionService.exit(100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("出场：已结束会话（终态不可重复出场）→ 守卫拒绝")
    void 出场已结束拒绝() {
        when(parkSessionDao.selectById(100L)).thenReturn(sessionWithState(ParkSessionState.FINISHED));

        assertThatThrownBy(() -> parkSessionService.exit(100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法的会话状态迁移");
        verify(parkSessionDao, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("出场：已取消会话（已取消不可结算）→ 守卫拒绝")
    void 出场已取消拒绝() {
        when(parkSessionDao.selectById(100L)).thenReturn(sessionWithState(ParkSessionState.CANCELLED));

        assertThatThrownBy(() -> parkSessionService.exit(100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法的会话状态迁移");
    }

    @Test
    @DisplayName("出场：会话终态化 0 行（并发下被取消抢先）→ 拒绝且不读规则不写账不释放")
    void 出场并发被抢先拒绝() {
        when(parkSessionDao.selectById(100L)).thenReturn(ongoingSession());
        when(parkSessionDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> parkSessionService.exit(100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("状态已变更");
        verify(feeRuleDao, never()).selectList(any());
        verify(parkSpaceDao, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("出场：无启用计费规则 → 业务异常（不生成订单）")
    void 出场无启用规则() {
        when(parkSessionDao.selectById(100L)).thenReturn(ongoingSession());
        when(parkSessionDao.update(isNull(), any())).thenReturn(1);
        when(feeRuleDao.selectList(any())).thenReturn(java.util.Collections.emptyList());

        assertThatThrownBy(() -> parkSessionService.exit(100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("无启用的计费规则");
    }

    @Test
    @DisplayName("出场：车位释放 0 行（会话与车位不一致）→ 回滚暴露")
    void 出场释放失败回滚() {
        when(parkSessionDao.selectById(100L)).thenReturn(ongoingSession());
        when(parkSessionDao.update(isNull(), any())).thenReturn(1);
        when(feeRuleDao.selectList(any())).thenReturn(java.util.List.of(enabledRule(200)));
        when(parkSpaceDao.update(isNull(), any())).thenReturn(0);
        doAnswer(inv -> {
            ParkOrderEntity o = inv.getArgument(0);
            o.setOrderId(200L);
            return 1;
        }).when(parkOrderDao).insert(any(ParkOrderEntity.class));

        assertThatThrownBy(() -> parkSessionService.exit(100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车位状态异常");
    }

    // ---------- helpers ----------

    private ParkSpaceEntity idleSpace() {
        return spaceWithState(ParkSpaceState.IDLE);
    }

    private ParkSpaceEntity spaceWithState(ParkSpaceState state) {
        ParkSpaceEntity space = new ParkSpaceEntity();
        space.setSpaceId(SPACE_ID);
        space.setSpaceNo(SPACE_NO);
        space.setSpaceState(state.getCode());
        return space;
    }

    private ParkSessionEntity ongoingSession() {
        return sessionWithState(ParkSessionState.ONGOING);
    }

    private ParkSessionEntity sessionWithState(ParkSessionState state) {
        ParkSessionEntity session = new ParkSessionEntity();
        session.setSessionId(100L);
        session.setSpaceId(SPACE_ID);
        session.setSpaceNo(SPACE_NO);
        session.setPlateNo("浙B12345");
        session.setSessionEntryTime(System.currentTimeMillis() / 1000);
        session.setSessionState(state.getCode());
        return session;
    }

    /** 进行中会话，入场时间在 secondsAgo 秒之前（构造可断言的停车时长） */
    private ParkSessionEntity sessionParkedSecondsAgo(long secondsAgo) {
        ParkSessionEntity session = sessionWithState(ParkSessionState.ONGOING);
        session.setSessionEntryTime(System.currentTimeMillis() / 1000 - secondsAgo);
        return session;
    }

    private FeeRuleEntity enabledRule(int priceFen) {
        FeeRuleEntity rule = new FeeRuleEntity();
        rule.setRuleId(1L);
        rule.setRuleName("标准计时收费");
        rule.setUnitPriceFen(priceFen);
        rule.setRuleState(FeeRuleState.ENABLED.getCode());
        return rule;
    }
}
