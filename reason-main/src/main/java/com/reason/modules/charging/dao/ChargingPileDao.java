package com.reason.modules.charging.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.charging.entity.ChargingPileEntity;
import org.springframework.stereotype.Repository;

/**
 * 充电桩台账（绑车位 1:1，空间主数据共享停车域）
 *
 * @date 2026-09-05
 */
@Repository
public interface ChargingPileDao extends BaseMapper<ChargingPileEntity> {
}
