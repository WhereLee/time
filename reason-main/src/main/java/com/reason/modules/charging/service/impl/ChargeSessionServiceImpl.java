package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Constant;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.charging.dao.ChargeFeeRuleDao;
import com.reason.modules.charging.dao.ChargeOrderDao;
import com.reason.modules.charging.dao.ChargeSessionDao;
import com.reason.modules.charging.dao.ChargingPileDao;
import com.reason.modules.charging.dao.BenefitRecordDao;
import com.reason.modules.charging.entity.ChargeFeeRuleEntity;
import com.reason.modules.charging.entity.ChargeOrderEntity;
import com.reason.modules.charging.entity.ChargeSessionEntity;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.enums.ChargeSessionState;
import com.reason.modules.charging.enums.PileState;
import com.reason.modules.charging.form.ChargeSessionForm;
import com.reason.modules.charging.service.ChargeFeeCalculator;
import com.reason.modules.charging.service.ChargeSessionService;
import com.reason.modules.parking.service.ParkSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 充电会话服务实现
 *
 * <p>并发正确性模型（充电开始/取消共用）：</p>
 * <ol>
 *   <li>守卫是<b>快速失败层</b>（语义化错误：桩不存在/停用、无停车会话、车牌错配、非法迁移）</li>
 *   <li>桩状态条件更新是<b>最终判官</b>——判定+写入压缩进一条 UPDATE，行锁串行化保证
 *       并发重复开始仅一方行数 &gt; 0（一桩一会话由桩状态唯一承载，无需会话表查重兜底）</li>
 * </ol>
 *
 * @date 2026-09-05
 */
@Slf4j
@Service("chargeSessionService")
public class ChargeSessionServiceImpl extends ServiceImpl<ChargeSessionDao, ChargeSessionEntity>
        implements ChargeSessionService {

    @Autowired
    private ChargingPileDao chargingPileDao;

    @Autowired
    private ChargeSessionDao chargeSessionDao;

    @Autowired
    private ChargeFeeRuleDao chargeFeeRuleDao;

    @Autowired
    private ChargeOrderDao chargeOrderDao;

    @Autowired
    private BenefitRecordDao benefitRecordDao;

    /**
     * 跨上下文只读能力（锚定停车会话），不直连 park_session 表——凭证化边界
     */
    @Autowired
    private ParkSessionService parkSessionService;

    /** 权益策略（M1 固定值，优惠引擎/规则化归 M2） */
    private static final int BENEFIT_FREE_SECONDS = 3600;   //免停时长：1 小时
    private static final long BENEFIT_EXPIRE_SECONDS = 24L * 3600;   //有效期：24 小时
    private static final DateTimeFormatter BN_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(String pileNo, String plateNo) {
        //1.入参校验与规范化（编号/车牌统一大写入库，设备上报不区分大小写）
        String normalizedPileNo = normalize(pileNo, "桩编号不能为空");
        String normalizedPlate = normalize(plateNo, "车牌号不能为空");

        //2.查桩：不存在/停用直接拒绝
        ChargingPileEntity pile = chargingPileDao.selectOne(new LambdaQueryWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getPileNo, normalizedPileNo));
        if (pile == null) {
            throw new RRException("充电桩不存在：" + normalizedPileNo);
        }
        if (PileState.of(pile.getPileState()) == PileState.DISABLED) {
            throw new RRException("充电桩已停用，不能充电：" + normalizedPileNo);
        }

        long now = System.currentTimeMillis() / 1000;

        //3.锚定停车会话（跨上下文只读能力）：该车位无进行中停车会话 → 拒绝（充电必须发生在停车中）
        ParkSessionService.OngoingParkSession ongoing = parkSessionService.getOngoingBySpaceId(pile.getSpaceId());
        if (ongoing == null) {
            throw new RRException("车位无进行中停车会话，不能开始充电（桩：" + normalizedPileNo + "）");
        }
        //车牌一致性校验：防凭证错配（张三的停车会话不能被李四的车充电绑定）
        if (!ongoing.plateNo().equals(normalizedPlate)) {
            throw new RRException("车牌与停车会话不一致，不能充电：上报=" + normalizedPlate
                    + "，停车会话=" + ongoing.plateNo());
        }

        //4.桩占位（最终判官）：空闲 → 充电中，行数 0 = 并发窗口内被抢先（重复开始被拒）
        int occupyRows = chargingPileDao.update(null, new LambdaUpdateWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getPileId, pile.getPileId())
                .eq(ChargingPileEntity::getPileState, PileState.IDLE.getCode())
                .set(ChargingPileEntity::getPileState, PileState.CHARGING.getCode())
                .set(ChargingPileEntity::getPileUpdatetime, now));
        if (occupyRows == 0) {
            throw new RRException("桩已被占用或状态变更，充电未开始：" + normalizedPileNo);
        }

        //5.创建充电中会话（anchor_session_id 在 start 时锁死，权益签发继承此锚定）
        ChargeSessionEntity session = new ChargeSessionEntity();
        session.setPileId(pile.getPileId());
        session.setPileNo(pile.getPileNo());
        session.setSpaceId(pile.getSpaceId());
        session.setSpaceNo(ongoing.spaceNo());
        session.setPlateNo(normalizedPlate);
        session.setAnchorSessionId(ongoing.sessionId());
        session.setSessionStartTime(now);
        session.setSessionState(ChargeSessionState.CHARGING.getCode());
        session.setSessionCreatetime(now);
        session.setSessionUpdatetime(now);
        chargeSessionDao.insert(session);
        return session.getSessionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long sessionId, String cancelReason) {
        if (sessionId == null) {
            throw new RRException("会话id不能为空");
        }

        //1.查会话：不存在直接拒绝
        ChargeSessionEntity session = chargeSessionDao.selectById(sessionId);
        if (session == null) {
            throw new RRException("充电会话不存在：" + sessionId);
        }

        //2.守卫（快速失败层）：非法迁移（终态不可取消）语义化拒绝
        ChargeSessionState cur = ChargeSessionState.of(session.getSessionState());
        ChargeSessionState.assertCanTransit(cur, ChargeSessionState.CANCELLED);

        long now = System.currentTimeMillis() / 1000;

        //3.会话终态化（最终判官）：条件更新 充电中→已取消
        //  并发下两个 cancel 同时读到充电中时，仅一方行数>0，另一方收到"状态已变更"
        int finishRows = chargeSessionDao.update(null, new LambdaUpdateWrapper<ChargeSessionEntity>()
                .eq(ChargeSessionEntity::getSessionId, sessionId)
                .eq(ChargeSessionEntity::getSessionState, ChargeSessionState.CHARGING.getCode())
                .set(ChargeSessionEntity::getSessionState, ChargeSessionState.CANCELLED.getCode())
                .set(ChargeSessionEntity::getSessionEndTime, now)
                .set(ChargeSessionEntity::getCancelReason, cancelReason)
                .set(ChargeSessionEntity::getSessionUpdatetime, now));
        if (finishRows == 0) {
            throw new RRException("会话状态已变更，请刷新后重试：" + sessionId);
        }

        //4.释放桩（条件更新 充电中→空闲）
        //  本事务已是会话终态化的唯一胜者，桩按会话状态必为充电中；
        //  行数 0 意味着会话与桩状态不一致（数据异常），宁可回滚暴露，不静默卡死桩
        int releaseRows = chargingPileDao.update(null, new LambdaUpdateWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getPileId, session.getPileId())
                .eq(ChargingPileEntity::getPileState, PileState.CHARGING.getCode())
                .set(ChargingPileEntity::getPileState, PileState.IDLE.getCode())
                .set(ChargingPileEntity::getPileUpdatetime, now));
        if (releaseRows == 0) {
            log.error("充电取消时桩释放失败（会话与桩状态不一致）：sessionId={}, pileId={}",
                    sessionId, session.getPileId());
            throw new RRException("桩状态异常，取消失败，请联系管理员：" + sessionId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long finish(Long sessionId, Long energyWh) {
        return settle(sessionId, energyWh, ChargeSessionState.FINISHED, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long timeoutFinish(Long sessionId, String reason) {
        //0 电量强制结束：巡检驱动的悬挂终态，不产生电量与权益
        return settle(sessionId, 0L, ChargeSessionState.TIMEOUT_FINISHED, reason);
    }

    /**
     * 结算公共链路（finish/timeoutFinish 共用）：判官终态化 → 读费率 → 订单快照 → 电量>0 签发权益 → 桩释放
     *
     * @param targetState 目标终态（已结束/超时结束）
     * @param reason      终态原因（仅取消/超时语义写入，正常结束为空）
     */
    private Long settle(Long sessionId, Long energyWh, ChargeSessionState targetState, String reason) {
        if (sessionId == null) {
            throw new RRException("会话id不能为空");
        }
        if (energyWh == null || energyWh < 0) {
            throw new RRException("电量不能为空且不能为负");
        }

        //1.查会话：不存在直接拒绝
        ChargeSessionEntity session = chargeSessionDao.selectById(sessionId);
        if (session == null) {
            throw new RRException("充电会话不存在：" + sessionId);
        }

        //2.守卫（快速失败层）：非法迁移（终态不可结束）语义化拒绝
        ChargeSessionState cur = ChargeSessionState.of(session.getSessionState());
        ChargeSessionState.assertCanTransit(cur, targetState);

        long now = System.currentTimeMillis() / 1000;

        //3.会话终态化（最终判官）：充电中 → 目标终态，电量落会话
        //  并发下 finish/cancel/timeout 同时读到充电中时仅一方行数>0，另一方收到"状态已变更"而非继续写账
        int finishRows = chargeSessionDao.update(null, new LambdaUpdateWrapper<ChargeSessionEntity>()
                .eq(ChargeSessionEntity::getSessionId, sessionId)
                .eq(ChargeSessionEntity::getSessionState, ChargeSessionState.CHARGING.getCode())
                .set(ChargeSessionEntity::getSessionState, targetState.getCode())
                .set(ChargeSessionEntity::getSessionEndTime, now)
                .set(ChargeSessionEntity::getEnergyWh, energyWh)
                .set(reason != null, ChargeSessionEntity::getCancelReason, reason)
                .set(ChargeSessionEntity::getSessionUpdatetime, now));
        if (finishRows == 0) {
            throw new RRException("会话状态已变更，请刷新后重试：" + sessionId);
        }

        //4.读启用费率并结算（本事务已是会话终态化的唯一胜者）
        //  启用费率 M1 约定仅一条；selectList 防御而非 selectOne（多条时 selectOne 抛 TooManyResults）
        List<ChargeFeeRuleEntity> enabledRules = chargeFeeRuleDao.selectList(new LambdaQueryWrapper<ChargeFeeRuleEntity>()
                .eq(ChargeFeeRuleEntity::getRuleState, 1));
        if (enabledRules.isEmpty()) {
            throw new RRException("无启用的充电费率，请联系管理员");
        }
        ChargeFeeRuleEntity rule = enabledRules.get(0);   //多策略选择（默认/峰谷）归 M2 费率引擎
        ChargeFeeCalculator.ChargeFeeResult fee = ChargeFeeCalculator.calculate(
                energyWh, rule.getElecPriceFen(), rule.getServicePriceFen());

        //5.生成订单快照（金额/单价/电量全部定格，规则后续调价不影响历史订单）
        ChargeOrderEntity order = new ChargeOrderEntity();
        order.setSessionId(sessionId);
        order.setPileNo(session.getPileNo());
        order.setSpaceNo(session.getSpaceNo());
        order.setPlateNo(session.getPlateNo());
        order.setOrderStartTime(session.getSessionStartTime());
        order.setOrderEndTime(now);
        order.setEnergyWh(energyWh);
        order.setElecPriceFen(rule.getElecPriceFen());
        order.setServicePriceFen(rule.getServicePriceFen());
        order.setElecAmountFen(fee.elecAmountFen());
        order.setServiceAmountFen(fee.serviceAmountFen());
        order.setAmountFen(fee.amountFen());
        order.setOrderState(0);
        order.setOrderCreatetime(now);
        chargeOrderDao.insert(order);

        //6.电量 > 0 时签发免停权益（0 Wh 不发权益：充电未生效不送权益，堵免费薅权益）
        //  锚定继承自充电会话（anchor_session_id 在 start 锁死），核销只能作用到锚定的停车会话
        if (energyWh > 0) {
            BenefitRecordEntity benefit = new BenefitRecordEntity();
            benefit.setBenefitNo(genBenefitNo());
            benefit.setSourceOrderId(order.getOrderId());
            benefit.setPlateNo(session.getPlateNo());
            benefit.setAnchorSessionId(session.getAnchorSessionId());
            benefit.setFreeSeconds(BENEFIT_FREE_SECONDS);
            benefit.setExpireTime(now + BENEFIT_EXPIRE_SECONDS);
            benefit.setBenefitState(0);
            benefit.setBenefitCreatetime(now);
            benefitRecordDao.insert(benefit);
            log.info("充电免停权益已签发：sessionId={}, orderId={}, benefitNo={}",
                    sessionId, order.getOrderId(), benefit.getBenefitNo());
        }

        //7.释放桩（条件更新 充电中→空闲）；行数 0 = 会话与桩状态不一致（数据异常），回滚暴露
        int releaseRows = chargingPileDao.update(null, new LambdaUpdateWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getPileId, session.getPileId())
                .eq(ChargingPileEntity::getPileState, PileState.CHARGING.getCode())
                .set(ChargingPileEntity::getPileState, PileState.IDLE.getCode())
                .set(ChargingPileEntity::getPileUpdatetime, now));
        if (releaseRows == 0) {
            log.error("充电结束时桩释放失败（会话与桩状态不一致）：sessionId={}, pileId={}",
                    sessionId, session.getPileId());
            throw new RRException("桩状态异常，结算失败，请联系管理员：" + sessionId);
        }
        return order.getOrderId();
    }

    /**
     * 权益码生成：BN + 时间戳 + 6 位随机（可读可追溯，防顺序枚举）；唯一索引兜底碰撞
     */
    private String genBenefitNo() {
        return "BN" + BN_TS.format(LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    @Override
    public PageUtils queryPage(ChargeSessionForm form) {
        IPage<ChargeSessionEntity> page = new Query<ChargeSessionEntity>().getPage(new MapUtils()
                .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        chargeSessionDao.selectPage(page, new LambdaQueryWrapper<ChargeSessionEntity>()
                .like(org.springframework.util.StringUtils.hasText(form.getPileNo()),
                        ChargeSessionEntity::getPileNo, form.getPileNo())
                .like(org.springframework.util.StringUtils.hasText(form.getPlateNo()),
                        ChargeSessionEntity::getPlateNo, form.getPlateNo())
                .eq(form.getSessionState() != null, ChargeSessionEntity::getSessionState, form.getSessionState())
                .orderByDesc(ChargeSessionEntity::getSessionId));
        return new PageUtils(page);
    }

    private String normalize(String value, String emptyMsg) {
        if (value == null || value.trim().isEmpty()) {
            throw new RRException(emptyMsg);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
