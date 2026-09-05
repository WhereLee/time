package com.reason.modules.charging.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.charging.entity.ChargeSessionEntity;
import org.springframework.stereotype.Repository;

/**
 * 充电会话（锚定停车会话，充电必须发生在停车中）
 *
 * @date 2026-09-05
 */
@Repository
public interface ChargeSessionDao extends BaseMapper<ChargeSessionEntity> {
}
