package com.reason.modules.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.device.entity.GateManualOpEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 闸机人工操作留痕 DAO（手动抬杆审计）
 *
 * @date 2026-09-06
 */
@Mapper
public interface GateManualOpDao extends BaseMapper<GateManualOpEntity> {
}
