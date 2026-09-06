package com.reason.modules.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.device.entity.DeviceRecordEntity;
import org.springframework.stereotype.Repository;

/**
 * 设备台账 DAO
 */
@Repository
public interface DeviceRecordDao extends BaseMapper<DeviceRecordEntity> {
}
