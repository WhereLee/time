package com.reason.modules.charging.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import org.springframework.stereotype.Repository;

/**
 * 免停权益（跨方凭证：charging 签发，parking 凭码核销）
 *
 * @date 2026-09-05
 */
@Repository
public interface BenefitRecordDao extends BaseMapper<BenefitRecordEntity> {
}
