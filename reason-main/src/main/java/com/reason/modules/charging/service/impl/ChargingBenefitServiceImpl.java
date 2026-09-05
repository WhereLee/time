package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.reason.modules.charging.dao.BenefitRecordDao;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.enums.BenefitState;
import com.reason.modules.charging.service.ChargingBenefitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 免停权益凭证能力实现
 *
 * <p>本类不持有自身事务——check/redeem 必须由调用方（parking 出场结算）的事务承载，
 * 保证凭证核销与停车订单同事务原子；幂等与并发裁决全部落在条件更新。</p>
 *
 * @date 2026-09-05
 */
@Slf4j
@Service("chargingBenefitService")
public class ChargingBenefitServiceImpl implements ChargingBenefitService {

    @Autowired
    private BenefitRecordDao benefitRecordDao;

    @Override
    public BenefitView check(String benefitNo) {
        if (benefitNo == null || benefitNo.trim().isEmpty()) {
            return null;
        }
        BenefitRecordEntity entity = benefitRecordDao.selectOne(new LambdaQueryWrapper<BenefitRecordEntity>()
                .eq(BenefitRecordEntity::getBenefitNo, benefitNo.trim())
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        return new BenefitView(entity.getBenefitNo(), entity.getFreeSeconds(), entity.getExpireTime(),
                entity.getBenefitState(), entity.getAnchorSessionId(), entity.getSourceOrderId());
    }

    @Override
    public boolean redeem(String benefitNo, Long parkSessionId, Long parkOrderId, long now) {
        if (benefitNo == null || benefitNo.trim().isEmpty() || parkSessionId == null || parkOrderId == null) {
            log.warn("凭证核销入参非法：benefitNo={}, parkSessionId={}, parkOrderId={}", benefitNo, parkSessionId, parkOrderId);
            return false;
        }
        //最终判官：权益码 + 可用态 + 锚定会话一致 三条件同时命中才核销（防双花/防错配/防重放）
        int rows = benefitRecordDao.update(null, new LambdaUpdateWrapper<BenefitRecordEntity>()
                .eq(BenefitRecordEntity::getBenefitNo, benefitNo.trim())
                .eq(BenefitRecordEntity::getBenefitState, BenefitState.AVAILABLE.getCode())
                .eq(BenefitRecordEntity::getAnchorSessionId, parkSessionId)
                .set(BenefitRecordEntity::getBenefitState, BenefitState.REDEEMED.getCode())
                .set(BenefitRecordEntity::getRedeemSessionId, parkSessionId)
                .set(BenefitRecordEntity::getRedeemOrderId, parkOrderId)
                .set(BenefitRecordEntity::getRedeemTime, now));
        if (rows == 0) {
            log.warn("凭证核销未命中（已核销/已过期/锚定错配）：benefitNo={}, parkSessionId={}", benefitNo, parkSessionId);
            return false;
        }
        log.info("凭证核销成功：benefitNo={}, parkSessionId={}, parkOrderId={}", benefitNo, parkSessionId, parkOrderId);
        return true;
    }
}
