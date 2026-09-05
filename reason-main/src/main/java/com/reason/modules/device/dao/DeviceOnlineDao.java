package com.reason.modules.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.device.entity.DeviceOnlineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备在线台账 DAO（闸机/位检/充电桩统一在线语义）
 *
 * @date 2026-09-06
 */
@Mapper
public interface DeviceOnlineDao extends BaseMapper<DeviceOnlineEntity> {
}
