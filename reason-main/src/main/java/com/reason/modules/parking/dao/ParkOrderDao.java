package com.reason.modules.parking.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.parking.entity.ParkOrderEntity;
import org.springframework.stereotype.Repository;

/**
 * 停车订单
 *
 * @date 2026-09-04
 */
@Repository
public interface ParkOrderDao extends BaseMapper<ParkOrderEntity> {
}
