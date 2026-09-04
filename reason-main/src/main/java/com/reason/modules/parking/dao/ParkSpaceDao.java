package com.reason.modules.parking.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import org.springframework.stereotype.Repository;

/**
 * 停车位台账
 *
 * @date 2026-09-04
 */
@Repository
public interface ParkSpaceDao extends BaseMapper<ParkSpaceEntity> {
}
