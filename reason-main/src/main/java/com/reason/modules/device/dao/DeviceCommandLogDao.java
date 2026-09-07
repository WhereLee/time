package com.reason.modules.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import org.springframework.stereotype.Repository;

/**
 * 设备指令流水 DAO
 */
@Repository
public interface DeviceCommandLogDao extends BaseMapper<DeviceCommandLogEntity> {
}
