package com.reason.modules.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.device.entity.DeviceManualHoldEntity;
import org.springframework.stereotype.Repository;

/**
 * 手动保持期 DAO
 */
@Repository
public interface DeviceManualHoldDao extends BaseMapper<DeviceManualHoldEntity> {
}
