package com.reason.modules.parking.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.parking.entity.FeeRuleEntity;
import org.springframework.stereotype.Repository;

/**
 * 计费规则
 *
 * @date 2026-09-04
 */
@Repository
public interface FeeRuleDao extends BaseMapper<FeeRuleEntity> {
}
