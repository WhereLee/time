package com.reason.modules.charging.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.charging.entity.ChargeFeeRuleEntity;
import org.springframework.stereotype.Repository;

/**
 * 充电费率（电费+服务费两段）
 *
 * @date 2026-09-05
 */
@Repository
public interface ChargeFeeRuleDao extends BaseMapper<ChargeFeeRuleEntity> {
}
