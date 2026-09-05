package com.reason.modules.charging.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.reason.modules.charging.dao.BenefitRecordDao;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.enums.BenefitState;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 免停权益过期作废任务（调度注册：schedule_job → benefitExpireTask，每分钟）
 *
 * <p>幂等与并发：单条条件更新批量作废（可用且已到期 → 已过期）——重复执行无新命中行；
 * 与出场核销并发时行锁裁决：核销先赢则本批该行不再命中（保持已核销，不覆盖）。</p>
 *
 * @date 2026-09-05
 */
@Slf4j
@Component("benefitExpireTask")
public class BenefitExpireTask implements ITask {

    @Autowired
    private BenefitRecordDao benefitRecordDao;

    @Override
    public void run(String params) {
        long now = System.currentTimeMillis() / 1000;
        int rows = benefitRecordDao.update(null, new LambdaUpdateWrapper<BenefitRecordEntity>()
                .eq(BenefitRecordEntity::getBenefitState, BenefitState.AVAILABLE.getCode())
                .le(BenefitRecordEntity::getExpireTime, now)
                .set(BenefitRecordEntity::getBenefitState, BenefitState.EXPIRED.getCode()));
        log.info("权益过期扫描完成：本次作废 {} 张（幂等条件更新，可重入）", rows);
    }
}
