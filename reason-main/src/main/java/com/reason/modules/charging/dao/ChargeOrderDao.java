package com.reason.modules.charging.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.charging.entity.ChargeOrderEntity;
import org.springframework.stereotype.Repository;

/**
 * 充电订单（电量两段计费快照，生成后不可变）
 *
 * @date 2026-09-05
 */
@Repository
public interface ChargeOrderDao extends BaseMapper<ChargeOrderEntity> {
}
