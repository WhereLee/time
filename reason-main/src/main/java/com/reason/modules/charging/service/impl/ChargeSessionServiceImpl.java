package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.modules.charging.dao.ChargeSessionDao;
import com.reason.modules.charging.dao.ChargingPileDao;
import com.reason.modules.charging.entity.ChargeSessionEntity;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.enums.ChargeSessionState;
import com.reason.modules.charging.enums.PileState;
import com.reason.modules.charging.service.ChargeSessionService;
import com.reason.modules.parking.service.ParkSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

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

    /**
     * 跨上下文只读能力（锚定停车会话），不直连 park_session 表——凭证化边界
     */
    @Autowired
    private ParkSessionService parkSessionService;

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

    private String normalize(String value, String emptyMsg) {
        if (value == null || value.trim().isEmpty()) {
            throw new RRException(emptyMsg);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
