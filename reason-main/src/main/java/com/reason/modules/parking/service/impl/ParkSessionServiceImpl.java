package com.reason.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.reason.modules.parking.service.FeeCalculator;
import com.reason.modules.parking.service.ParkSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 停车会话服务实现
 *
 * <p>并发正确性模型（入场/取消共用）：</p>
 * <ol>
 *   <li>守卫与查重是<b>快速失败层</b>（语义化错误、避免无谓写竞争）</li>
 *   <li>条件更新是<b>最终判官</b>——判定+写入压缩进一条 UPDATE，
 *       行锁串行化保证并发下仅一方影响行数 &gt; 0</li>
 * </ol>
 *
 * @date 2026-09-04
 */
@Slf4j
@Service("parkSessionService")
public class ParkSessionServiceImpl extends ServiceImpl<ParkSessionDao, ParkSessionEntity>
        implements ParkSessionService {

    @Autowired
    private ParkSpaceDao parkSpaceDao;

    @Autowired
    private ParkSessionDao parkSessionDao;

    @Autowired
    private FeeRuleDao feeRuleDao;

    @Autowired
    private ParkOrderDao parkOrderDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long entry(String spaceNo, String plateNo) {
        //1.入参校验与规范化（车牌/编号统一大写入库，设备上报不区分大小写）
        String normalizedSpaceNo = normalize(spaceNo, "车位编号不能为空");
        String normalizedPlate = normalize(plateNo, "车牌号不能为空");

        //2.查车位：不存在/禁用直接拒绝
        ParkSpaceEntity space = parkSpaceDao.selectOne(new LambdaQueryWrapper<ParkSpaceEntity>()
                .eq(ParkSpaceEntity::getSpaceNo, normalizedSpaceNo));
        if (space == null) {
            throw new RRException("车位不存在：" + normalizedSpaceNo);
        }
        if (ParkSpaceState.of(space.getSpaceState()) == ParkSpaceState.DISABLED) {
            throw new RRException("车位已禁用，不能入场：" + normalizedSpaceNo);
        }

        long now = System.currentTimeMillis() / 1000;

        //3.查重兜底（快速失败层）：该车位已有进行中会话 → 拒绝
        //  正确性不依赖此步——它与条件更新之间的窗口由第 4 步的行锁语义闭合
        Long ongoingCount = parkSessionDao.selectCount(new LambdaQueryWrapper<ParkSessionEntity>()
                .eq(ParkSessionEntity::getSpaceId, space.getSpaceId())
                .eq(ParkSessionEntity::getSessionState, ParkSessionState.ONGOING.getCode()));
        if (ongoingCount != null && ongoingCount > 0) {
            throw new RRException("车位已被占用：" + normalizedSpaceNo);
        }

        //4.条件更新占位（最终判官）：空闲 → 占用，行数 0 = 并发窗口内被抢先
        int occupyRows = parkSpaceDao.update(null, new LambdaUpdateWrapper<ParkSpaceEntity>()
                .eq(ParkSpaceEntity::getSpaceId, space.getSpaceId())
                .eq(ParkSpaceEntity::getSpaceState, ParkSpaceState.IDLE.getCode())
                .set(ParkSpaceEntity::getSpaceState, ParkSpaceState.OCCUPIED.getCode())
                .set(ParkSpaceEntity::getSpaceUpdatetime, now));
        if (occupyRows == 0) {
            throw new RRException("车位已被占用：" + normalizedSpaceNo);
        }

        //5.创建进行中会话（入场时间即创建时间）
        ParkSessionEntity session = new ParkSessionEntity();
        session.setSpaceId(space.getSpaceId());
        session.setSpaceNo(space.getSpaceNo());
        session.setPlateNo(normalizedPlate);
        session.setSessionEntryTime(now);
        session.setSessionState(ParkSessionState.ONGOING.getCode());
        session.setSessionUpdatetime(now);
        parkSessionDao.insert(session);
        return session.getSessionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long sessionId, String cancelReason) {
        if (sessionId == null) {
            throw new RRException("会话id不能为空");
        }

        //1.查会话：不存在直接拒绝
        ParkSessionEntity session = parkSessionDao.selectById(sessionId);
        if (session == null) {
            throw new RRException("停车会话不存在：" + sessionId);
        }

        //2.守卫（快速失败层）：非法迁移（终态不可取消）语义化拒绝
        ParkSessionState cur = ParkSessionState.of(session.getSessionState());
        ParkSessionState.assertCanTransit(cur, ParkSessionState.CANCELLED);

        long now = System.currentTimeMillis() / 1000;

        //3.会话终态化（最终判官）：条件更新 进行中→已取消
        //  并发下两个 cancel 同时读到进行中时，仅一方行数>0，另一方收到"状态已变更"而非覆盖写入
        int finishRows = parkSessionDao.update(null, new LambdaUpdateWrapper<ParkSessionEntity>()
                .eq(ParkSessionEntity::getSessionId, sessionId)
                .eq(ParkSessionEntity::getSessionState, ParkSessionState.ONGOING.getCode())
                .set(ParkSessionEntity::getSessionState, ParkSessionState.CANCELLED.getCode())
                .set(ParkSessionEntity::getSessionCancelTime, now)
                .set(ParkSessionEntity::getSessionCancelReason, cancelReason)
                .set(ParkSessionEntity::getSessionUpdatetime, now));
        if (finishRows == 0) {
            throw new RRException("会话状态已变更，请刷新后重试：" + sessionId);
        }

        //4.释放车位（条件更新 占用→空闲）
        //  本事务已是会话终态化的唯一胜者，车位按会话状态必为占用；
        //  行数 0 意味着会话与车位状态不一致（数据异常），宁可回滚暴露，不静默卡死车位
        int releaseRows = parkSpaceDao.update(null, new LambdaUpdateWrapper<ParkSpaceEntity>()
                .eq(ParkSpaceEntity::getSpaceId, session.getSpaceId())
                .eq(ParkSpaceEntity::getSpaceState, ParkSpaceState.OCCUPIED.getCode())
                .set(ParkSpaceEntity::getSpaceState, ParkSpaceState.IDLE.getCode())
                .set(ParkSpaceEntity::getSpaceUpdatetime, now));
        if (releaseRows == 0) {
            log.error("会话取消时车位释放失败（会话与车位状态不一致）：sessionId={}, spaceId={}", sessionId, session.getSpaceId());
            throw new RRException("车位状态异常，取消失败，请联系管理员：" + sessionId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long exit(Long sessionId) {
        if (sessionId == null) {
            throw new RRException("会话id不能为空");
        }

        //1.查会话：不存在直接拒绝
        ParkSessionEntity session = parkSessionDao.selectById(sessionId);
        if (session == null) {
            throw new RRException("停车会话不存在：" + sessionId);
        }

        //2.守卫（快速失败层）：非法迁移（终态/取消不可出场）语义化拒绝
        ParkSessionState cur = ParkSessionState.of(session.getSessionState());
        ParkSessionState.assertCanTransit(cur, ParkSessionState.FINISHED);

        long now = System.currentTimeMillis() / 1000;

        //3.会话终态化（最终判官）：进行中 → 已结束
        //  并发出场/取消同时读到进行中时仅一方行数>0，另一方收到"状态已变更"而非继续写账
        int finishRows = parkSessionDao.update(null, new LambdaUpdateWrapper<ParkSessionEntity>()
                .eq(ParkSessionEntity::getSessionId, sessionId)
                .eq(ParkSessionEntity::getSessionState, ParkSessionState.ONGOING.getCode())
                .set(ParkSessionEntity::getSessionState, ParkSessionState.FINISHED.getCode())
                .set(ParkSessionEntity::getSessionExitTime, now)
                .set(ParkSessionEntity::getSessionUpdatetime, now));
        if (finishRows == 0) {
            throw new RRException("会话状态已变更，请刷新后重试：" + sessionId);
        }

        //4.读启用规则并生成订单快照（本事务已是会话终态化的唯一胜者）
        //  启用规则 M0 约定仅一条；selectList 防御而非 selectOne（多条时 selectOne 抛 TooManyResults）
        List<FeeRuleEntity> enabledRules = feeRuleDao.selectList(new LambdaQueryWrapper<FeeRuleEntity>()
                .eq(FeeRuleEntity::getRuleState, FeeRuleState.ENABLED.getCode()));
        if (enabledRules.isEmpty()) {
            throw new RRException("无启用的计费规则，请联系管理员");
        }
        FeeRuleEntity rule = enabledRules.get(0);   //多策略选择（取默认/最优）归 M2 规则引擎

        long durationSeconds = now - session.getSessionEntryTime();
        ParkOrderEntity order = new ParkOrderEntity();
        order.setSessionId(sessionId);
        order.setPlateNo(session.getPlateNo());
        order.setSpaceNo(session.getSpaceNo());
        order.setOrderEntryTime(session.getSessionEntryTime());
        order.setOrderExitTime(now);
        order.setDurationMinutes(FeeCalculator.calcDurationMinutes(durationSeconds));
        order.setUnitPriceFen(rule.getUnitPriceFen());
        order.setAmountFen(FeeCalculator.calcAmountFen(durationSeconds, rule.getUnitPriceFen()));
        order.setOrderState(0);
        order.setOrderCreatetime(now);
        parkOrderDao.insert(order);

        //5.释放车位（条件更新 占用→空闲）；行数 0 = 会话与车位状态不一致 → 回滚暴露
        int releaseRows = parkSpaceDao.update(null, new LambdaUpdateWrapper<ParkSpaceEntity>()
                .eq(ParkSpaceEntity::getSpaceId, session.getSpaceId())
                .eq(ParkSpaceEntity::getSpaceState, ParkSpaceState.OCCUPIED.getCode())
                .set(ParkSpaceEntity::getSpaceState, ParkSpaceState.IDLE.getCode())
                .set(ParkSpaceEntity::getSpaceUpdatetime, now));
        if (releaseRows == 0) {
            log.error("出场结算时车位释放失败（会话与车位状态不一致）：sessionId={}, spaceId={}", sessionId, session.getSpaceId());
            throw new RRException("车位状态异常，出场失败，请联系管理员：" + sessionId);
        }
        return order.getOrderId();
    }

    private String normalize(String value, String emptyMsg) {
        if (value == null || value.trim().isEmpty()) {
            throw new RRException(emptyMsg);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
