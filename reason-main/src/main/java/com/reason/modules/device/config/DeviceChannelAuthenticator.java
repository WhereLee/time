package com.reason.modules.device.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 设备通道鉴权（0.5 per-device HMAC——共享口令退役，T1 修复）
 *
 * <p>事件/心跳上行请求携 X-Device-No + X-Device-Sign：平台按设备号查 DB 密钥，
 * HMAC 常量时间比对。防重放分工：事件由台账序守卫拒（0.1），心跳重放幂等无害。
 * 失败统一 401（0.7：设备通道不再 200+code500——token 漂移/伪造两侧立即可见）。</p>
 */
@Component
public class DeviceChannelAuthenticator {

    private final DeviceRecordDao recordDao;

    public DeviceChannelAuthenticator(DeviceRecordDao recordDao) {
        this.recordDao = recordDao;
    }

    /**
     * 校验事件签名（协议 v2 §3.1）
     */
    public void authenticateEvent(String deviceNo, Integer state, Long commandSeq,
                                  String bootId, Long eventSeq, String signature) {
        String secret = secretOf(deviceNo);
        if (secret == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "设备未登记或未配置密钥: " + deviceNo);
        }
        String canonical = DeviceSignature.canonicalEvent(deviceNo, state, commandSeq, bootId, eventSeq);
        if (!DeviceSignature.verify(secret, canonical, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "设备签名无效: " + deviceNo);
        }
    }

    /**
     * 校验心跳签名（canonical = deviceNo|state；state 为空=只报活）
     */
    public void authenticateHeartbeat(String deviceNo, Integer state, String signature) {
        String secret = secretOf(deviceNo);
        if (secret == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "设备未登记或未配置密钥: " + deviceNo);
        }
        String canonical = DeviceSignature.canonicalHeartbeat(deviceNo, state);
        if (!DeviceSignature.verify(secret, canonical, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "设备签名无效: " + deviceNo);
        }
    }

    /**
     * 按设备号取密钥（null=未登记/未配置——fail secure）
     */
    private String secretOf(String deviceNo) {
        DeviceRecordEntity record = recordDao.selectOne(new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
        if (record == null || record.getDeviceSecret() == null || record.getDeviceSecret().isEmpty()) {
            return null;
        }
        return record.getDeviceSecret();
    }
}
