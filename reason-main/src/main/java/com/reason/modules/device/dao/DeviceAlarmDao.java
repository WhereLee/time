package com.reason.modules.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.device.entity.DeviceAlarmEntity;
import org.springframework.stereotype.Repository;

/**
 * 设备告警 DAO
 */
@Repository
public interface DeviceAlarmDao extends BaseMapper<DeviceAlarmEntity> {
}
