package com.reason.modules.parking.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.parking.entity.ParkSessionEntity;
import org.springframework.stereotype.Repository;

/**
 * 停车会话
 *
 * @date 2026-09-04
 */
@Repository
public interface ParkSessionDao extends BaseMapper<ParkSessionEntity> {
}
