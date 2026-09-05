package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.reason.common.exception.RRException;
import com.reason.modules.charging.dao.BenefitRecordDao;
import com.reason.modules.charging.dao.ChargeFeeRuleDao;
import com.reason.modules.charging.dao.ChargeOrderDao;
import com.reason.modules.charging.dao.ChargeSessionDao;
import com.reason.modules.charging.dao.ChargingPileDao;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.entity.ChargeFeeRuleEntity;
import com.reason.modules.charging.entity.ChargeOrderEntity;
import com.reason.modules.charging.entity.ChargeSessionEntity;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.enums.ChargeSessionState;
import com.reason.modules.charging.enums.PileState;
import com.reason.modules.parking.service.ParkSessionService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充电会话服务单元测试（开始/取消事务矩阵）
 *
 * <p>并发正确性分层验证：本层 mock 验证「锚定链守卫 + 行数 0 → 拒绝」等分支逻辑；
 * 真实并发（并发重复开始仅一条生效）由 M1-5 后的真库 IT 在 CI 容器内验证。</p>
 */
@DisplayName("充电会话服务")
@ExtendWith(MockitoExtension.class)
class ChargeSessionServiceImplTest {

    private static final long PILE_ID = 1L;
    private static final String PILE_NO = "PILE-001";
    private static final long SPACE_ID = 10L;
    private static final String SPACE_NO = "B-001";
    private static final long PARK_SESSION_ID = 100L;
    private static final String PLATE = "浙B12345";

    @Mock
    private ChargingPileDao chargingPileDao;
    @Mock
    private ChargeSessionDao chargeSessionDao;
    @Mock
    private ChargeFeeRuleDao chargeFeeRuleDao;
    @Mock
    private ChargeOrderDao chargeOrderDao;
    @Mock
    private BenefitRecordDao benefitRecordDao;
    @Mock
    private ParkSessionService parkSessionService;
    @InjectMocks
    private ChargeSessionServiceImpl chargeSessionService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        //纯单测无 Spring 上下文：Lambda 包装器依赖 MP TableInfo 元数据缓存，需手动初始化目标实体
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChargingPileEntity.class);
        TableInfoHelper.initTableInfo(assistant, ChargeSessionEntity.class);
        TableInfoHelper.initTableInfo(assistant, ChargeFeeRuleEntity.class);
        TableInfoHelper.initTableInfo(assistant, ChargeOrderEntity.class);
        TableInfoHelper.initTableInfo(assistant, BenefitRecordEntity.class);
    }

    // ---------- 充电开始 ----------

    @Test
    @DisplayName("开始成功：锚定停车会话 + 桩占位 + 创建充电中会话（车牌规范化大写）")
    void 充电开始成功() {
        when(chargingPileDao.selectOne(any())).thenReturn(idlePile());
        when(parkSessionService.getOngoingBySpaceId(SPACE_ID))
                .thenReturn(new ParkSessionService.OngoingParkSession(PARK_SESSION_ID, PLATE, SPACE_NO));
        when(chargingPileDao.update(isNull(), any())).thenReturn(1);
        doAnswer(inv -> {
            ChargeSessionEntity e = inv.getArgument(0);
            e.setSessionId(200L);
            return 1;
        }).when(chargeSessionDao).insert(any(ChargeSessionEntity.class));

        Long sessionId = chargeSessionService.start(" pile-001 ", " 浙b12345 ");

        assertThat(sessionId).isEqualTo(200L);
        ArgumentCaptor<ChargeSessionEntity> captor = ArgumentCaptor.forClass(ChargeSessionEntity.class);
        verify(chargeSessionDao).insert(captor.capture());
        ChargeSessionEntity inserted = captor.getValue();
        assertThat(inserted.getPileId()).isEqualTo(PILE_ID);
        assertThat(inserted.getPileNo()).isEqualTo(PILE_NO);
        assertThat(inserted.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(inserted.getSpaceNo()).isEqualTo(SPACE_NO);            //锚定会话冗余自车位
        assertThat(inserted.getPlateNo()).isEqualTo(PLATE);                //trim + 大写
        assertThat(inserted.getAnchorSessionId()).isEqualTo(PARK_SESSION_ID);  //锚定锁死
        assertThat(inserted.getSessionState()).isEqualTo(ChargeSessionState.CHARGING.getCode());
        assertThat(inserted.getSessionStartTime()).isNotNull();
    }

    @Test
    @DisplayName("开始：桩不存在 → 业务异常")
    void 充电开始桩不存在() {
        when(chargingPileDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> chargeSessionService.start(PILE_NO, PLATE))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("充电桩不存在");
    }

    @Test
    @DisplayName("开始：桩已停用 → 业务异常")
    void 充电开始桩停用() {
        when(chargingPileDao.selectOne(any())).thenReturn(pileWithState(PileState.DISABLED));

        assertThatThrownBy(() -> chargeSessionService.start(PILE_NO, PLATE))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    @DisplayName("开始：车位无进行中停车会话 → 拒绝（充电必须发生在停车中）")
    void 充电开始无停车会话() {
        when(chargingPileDao.selectOne(any())).thenReturn(idlePile());
        when(parkSessionService.getOngoingBySpaceId(SPACE_ID)).thenReturn(null);

        assertThatThrownBy(() -> chargeSessionService.start(PILE_NO, PLATE))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("无进行中停车会话");
    }

    @Test
    @DisplayName("开始：车牌与停车会话不一致 → 拒绝（防凭证错配）")
    void 充电开始车牌错配() {
        when(chargingPileDao.selectOne(any())).thenReturn(idlePile());
        when(parkSessionService.getOngoingBySpaceId(SPACE_ID))
                .thenReturn(new ParkSessionService.OngoingParkSession(PARK_SESSION_ID, "浙C99999", SPACE_NO));

        assertThatThrownBy(() -> chargeSessionService.start(PILE_NO, PLATE))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    @DisplayName("开始：桩判官 0 行（并发重复开始被抢先）→ 拒绝且不再插入会话")
    void 充电开始并发重复() {
        when(chargingPileDao.selectOne(any())).thenReturn(idlePile());
        when(parkSessionService.getOngoingBySpaceId(SPACE_ID))
                .thenReturn(new ParkSessionService.OngoingParkSession(PARK_SESSION_ID, PLATE, SPACE_NO));
        when(chargingPileDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> chargeSessionService.start(PILE_NO, PLATE))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已被占用或状态变更");
    }

    @Test
    @DisplayName("开始：桩编号/车牌为空 → 业务异常")
    void 充电开始入参为空() {
        assertThatThrownBy(() -> chargeSessionService.start("  ", PLATE))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("桩编号不能为空");
        assertThatThrownBy(() -> chargeSessionService.start(PILE_NO, ""))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("车牌号不能为空");
    }

    // ---------- 取消充电 ----------

    @Test
    @DisplayName("取消成功：会话终态化（0→2）+ 桩释放回空闲")
    void 充电取消成功() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(1);
        when(chargingPileDao.update(isNull(), any())).thenReturn(1);

        chargeSessionService.cancel(200L, "测试取消");

        verify(chargeSessionDao).update(isNull(), any());
        verify(chargingPileDao).update(isNull(), any());
    }

    @Test
    @DisplayName("取消：会话不存在 → 业务异常")
    void 充电取消会话不存在() {
        when(chargeSessionDao.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> chargeSessionService.cancel(999L, "取消"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("充电会话不存在");
    }

    @Test
    @DisplayName("取消：会话 id 为空 → 业务异常")
    void 充电取消id为空() {
        assertThatThrownBy(() -> chargeSessionService.cancel(null, "取消"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("会话id不能为空");
    }

    @Test
    @DisplayName("取消：终态会话（已结束）→ 非法迁移拒绝，不触发任何更新")
    void 充电取消非法迁移() {
        ChargeSessionEntity finished = chargingSession();
        finished.setSessionState(ChargeSessionState.FINISHED.getCode());
        when(chargeSessionDao.selectById(200L)).thenReturn(finished);

        assertThatThrownBy(() -> chargeSessionService.cancel(200L, "取消"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法");
        verify(chargeSessionDao, org.mockito.Mockito.never()).update(isNull(), any());
    }

    @Test
    @DisplayName("取消：判官 0 行（并发已被抢先终态化）→ 业务异常")
    void 充电取消并发抢先() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> chargeSessionService.cancel(200L, "取消"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("状态已变更");
    }

    @Test
    @DisplayName("取消：桩释放 0 行（会话与桩状态不一致）→ 回滚暴露，不静默卡死桩")
    void 充电取消桩释放失败() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(1);
        when(chargingPileDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> chargeSessionService.cancel(200L, "取消"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("桩状态异常");
    }

    // ---------- 结束充电（结算 + 权益签发） ----------

    @Test
    @DisplayName("结束成功：判官终态化 + 两段费率订单快照 + 权益签发（锚定继承/有效期/权益码格式）")
    void 充电结束成功() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(1);
        when(chargeFeeRuleDao.selectList(any())).thenReturn(java.util.List.of(enabledRule()));
        doAnswer(inv -> {
            ChargeOrderEntity e = inv.getArgument(0);
            e.setOrderId(3000L);
            return 1;
        }).when(chargeOrderDao).insert(any(ChargeOrderEntity.class));
        doAnswer(inv -> {
            BenefitRecordEntity e = inv.getArgument(0);
            e.setBenefitId(4000L);
            return 1;
        }).when(benefitRecordDao).insert(any(BenefitRecordEntity.class));
        when(chargingPileDao.update(isNull(), any())).thenReturn(1);
        long before = System.currentTimeMillis() / 1000;

        Long orderId = chargeSessionService.finish(200L, 30_000L);

        assertThat(orderId).isEqualTo(3000L);
        //订单快照断言：金额=两段各自取整后相加（30 kWh × 80/40 → 2400/1200/3600）
        ArgumentCaptor<ChargeOrderEntity> orderCaptor = ArgumentCaptor.forClass(ChargeOrderEntity.class);
        verify(chargeOrderDao).insert(orderCaptor.capture());
        ChargeOrderEntity order = orderCaptor.getValue();
        assertThat(order.getSessionId()).isEqualTo(200L);
        assertThat(order.getPileNo()).isEqualTo(PILE_NO);
        assertThat(order.getSpaceNo()).isEqualTo(SPACE_NO);
        assertThat(order.getPlateNo()).isEqualTo(PLATE);
        assertThat(order.getEnergyWh()).isEqualTo(30_000L);
        assertThat(order.getElecPriceFen()).isEqualTo(80);
        assertThat(order.getServicePriceFen()).isEqualTo(40);
        assertThat(order.getElecAmountFen()).isEqualTo(2400L);
        assertThat(order.getServiceAmountFen()).isEqualTo(1200L);
        assertThat(order.getAmountFen()).isEqualTo(3600L);
        assertThat(order.getOrderStartTime()).isNotNull();
        assertThat(order.getOrderEndTime()).isGreaterThanOrEqualTo(order.getOrderStartTime());
        //权益签发断言：凭证码格式 BN+14 位时间戳+6 位随机；免停 1h；有效期 24h；锚定继承停车会话
        ArgumentCaptor<BenefitRecordEntity> benCaptor = ArgumentCaptor.forClass(BenefitRecordEntity.class);
        verify(benefitRecordDao).insert(benCaptor.capture());
        BenefitRecordEntity benefit = benCaptor.getValue();
        assertThat(benefit.getBenefitNo()).startsWith("BN");
        assertThat(benefit.getBenefitNo()).hasSize(22);
        assertThat(benefit.getSourceOrderId()).isEqualTo(3000L);
        assertThat(benefit.getPlateNo()).isEqualTo(PLATE);
        assertThat(benefit.getAnchorSessionId()).isEqualTo(PARK_SESSION_ID);
        assertThat(benefit.getFreeSeconds()).isEqualTo(3600);
        assertThat(benefit.getExpireTime() - before).isBetween(86_400L - 2, 86_400L + 2);
        assertThat(benefit.getBenefitState()).isZero();
        assertThat(benefit.getBenefitCreatetime()).isNotNull();
        verify(chargingPileDao).update(isNull(), any());
    }

    @Test
    @DisplayName("结束：0 电量合法结束 → 0 元订单但不签发权益（堵免费薅权益）")
    void 充电结束零电量不签权益() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(1);
        when(chargeFeeRuleDao.selectList(any())).thenReturn(java.util.List.of(enabledRule()));
        doAnswer(inv -> {
            ChargeOrderEntity e = inv.getArgument(0);
            e.setOrderId(3000L);
            return 1;
        }).when(chargeOrderDao).insert(any(ChargeOrderEntity.class));
        when(chargingPileDao.update(isNull(), any())).thenReturn(1);

        Long orderId = chargeSessionService.finish(200L, 0L);

        assertThat(orderId).isEqualTo(3000L);
        //0 Wh 不签发权益（异常路径由“异常已抛”保证，此处成功路径断言无签发）
        org.mockito.Mockito.verify(benefitRecordDao, org.mockito.Mockito.never())
                .insert(any(BenefitRecordEntity.class));
    }

    @Test
    @DisplayName("结束：会话不存在/电量非法 → 业务异常")
    void 充电结束入参非法() {
        when(chargeSessionDao.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> chargeSessionService.finish(999L, 100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("充电会话不存在");
        assertThatThrownBy(() -> chargeSessionService.finish(200L, null))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("电量不能为空");
        assertThatThrownBy(() -> chargeSessionService.finish(200L, -1L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("不能为负");
    }

    @Test
    @DisplayName("结束：终态会话（已取消）→ 非法迁移拒绝")
    void 充电结束非法迁移() {
        ChargeSessionEntity cancelled = chargingSession();
        cancelled.setSessionState(ChargeSessionState.CANCELLED.getCode());
        when(chargeSessionDao.selectById(200L)).thenReturn(cancelled);

        assertThatThrownBy(() -> chargeSessionService.finish(200L, 100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法");
    }

    @Test
    @DisplayName("结束：判官 0 行（并发被 cancel 抢先）→ 拒绝写账")
    void 充电结束并发抢先() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> chargeSessionService.finish(200L, 100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("状态已变更");
    }

    @Test
    @DisplayName("结束：无启用费率 → 业务异常（判官后置，回滚会话终态化）")
    void 充电结束无启用费率() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(1);
        when(chargeFeeRuleDao.selectList(any())).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> chargeSessionService.finish(200L, 100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("无启用的充电费率");
    }

    @Test
    @DisplayName("结束：桩释放 0 行（会话与桩状态不一致）→ 回滚暴露")
    void 充电结束桩释放失败() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(1);
        when(chargeFeeRuleDao.selectList(any())).thenReturn(java.util.List.of(enabledRule()));
        doAnswer(inv -> {
            ChargeOrderEntity e = inv.getArgument(0);
            e.setOrderId(3000L);
            return 1;
        }).when(chargeOrderDao).insert(any(ChargeOrderEntity.class));
        when(chargingPileDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> chargeSessionService.finish(200L, 100L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("桩状态异常");
    }

    // ---------- 超时强制结束（调度巡检驱动） ----------

    @Test
    @DisplayName("超时结束：悬挂会话 → state=3 终态 + 0 电 0 元订单 + 不发权益 + 桩释放")
    void 超时强制结束成功() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(1);
        when(chargeFeeRuleDao.selectList(any())).thenReturn(java.util.List.of(enabledRule()));
        doAnswer(inv -> {
            ChargeOrderEntity e = inv.getArgument(0);
            e.setOrderId(3000L);
            return 1;
        }).when(chargeOrderDao).insert(any(ChargeOrderEntity.class));
        when(chargingPileDao.update(isNull(), any())).thenReturn(1);

        Long orderId = chargeSessionService.timeoutFinish(200L, "超时巡检");

        assertThat(orderId).isEqualTo(3000L);
        //0 电 0 元订单（结算闭环可对账）
        ArgumentCaptor<ChargeOrderEntity> orderCaptor = ArgumentCaptor.forClass(ChargeOrderEntity.class);
        verify(chargeOrderDao).insert(orderCaptor.capture());
        ChargeOrderEntity order = orderCaptor.getValue();
        assertThat(order.getEnergyWh()).isZero();
        assertThat(order.getElecAmountFen()).isZero();
        assertThat(order.getServiceAmountFen()).isZero();
        assertThat(order.getAmountFen()).isZero();
        //0 电量不签发权益（堵免费薅权益）
        org.mockito.Mockito.verify(benefitRecordDao, org.mockito.Mockito.never())
                .insert(any(BenefitRecordEntity.class));
        verify(chargingPileDao).update(isNull(), any());
    }

    @Test
    @DisplayName("超时结束：终态会话（已结束）→ 非法迁移拒绝（巡检不覆盖正常终态）")
    void 超时结束非法迁移() {
        ChargeSessionEntity finished = chargingSession();
        finished.setSessionState(ChargeSessionState.FINISHED.getCode());
        when(chargeSessionDao.selectById(200L)).thenReturn(finished);

        assertThatThrownBy(() -> chargeSessionService.timeoutFinish(200L, "超时巡检"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法");
    }

    @Test
    @DisplayName("超时结束：判官 0 行（并发被 cancel 抢先）→ 拒绝，下轮重扫不再命中")
    void 超时结束并发抢先() {
        when(chargeSessionDao.selectById(200L)).thenReturn(chargingSession());
        when(chargeSessionDao.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> chargeSessionService.timeoutFinish(200L, "超时巡检"))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("状态已变更");
    }

    // ---------- 夹具 ----------

    private ChargingPileEntity idlePile() {
        return pileWithState(PileState.IDLE);
    }

    private ChargingPileEntity pileWithState(PileState state) {
        ChargingPileEntity pile = new ChargingPileEntity();
        pile.setPileId(PILE_ID);
        pile.setPileNo(PILE_NO);
        pile.setSpaceId(SPACE_ID);
        pile.setPileState(state.getCode());
        return pile;
    }

    private ChargeSessionEntity chargingSession() {
        ChargeSessionEntity session = new ChargeSessionEntity();
        session.setSessionId(200L);
        session.setPileId(PILE_ID);
        session.setPileNo(PILE_NO);
        session.setSpaceId(SPACE_ID);
        session.setSpaceNo(SPACE_NO);
        session.setPlateNo(PLATE);
        session.setAnchorSessionId(PARK_SESSION_ID);
        session.setSessionStartTime(System.currentTimeMillis() / 1000 - 100);
        session.setSessionState(ChargeSessionState.CHARGING.getCode());
        return session;
    }

    private ChargeFeeRuleEntity enabledRule() {
        ChargeFeeRuleEntity rule = new ChargeFeeRuleEntity();
        rule.setRuleId(1L);
        rule.setRuleName("标准充电费率");
        rule.setElecPriceFen(80);
        rule.setServicePriceFen(40);
        rule.setRuleState(1);
        return rule;
    }
}
