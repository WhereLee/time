package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.exception.RRException;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceCommandLogEntity;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.TriggerType;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备指令服务实现
 *
 * <p>指令下发 = 平台唯一的"指挥"动作：查档案 → seq 幂等键 → 记流水 → HTTP 发给设备 →
 * 设备立刻回执 ACCEPTED（动作异步执行，事件随后主动上报）。
 * 流水账本让"指令发出后"不再盲目：等事件推进 ARRIVED，超时由监控任务重试/告警。</p>
 */
@Slf4j
@Service("deviceCommandService")
public class DeviceCommandServiceImpl implements DeviceCommandService {

    /** 指令端点相对路径（模拟器侧 /cmd，与 reason-barrier-sim 契约一致） */
    private static final String CMD_PATH = "/cmd";

    private final DeviceRecordDao recordDao;
    private final DeviceChannelProperties channelProperties;
    private final DeviceCommandLogService commandLogService;
    private final RestTemplate restTemplate;

    public DeviceCommandServiceImpl(DeviceRecordDao recordDao,
                                    DeviceChannelProperties channelProperties,
                                    DeviceCommandLogService commandLogService) {
        this.recordDao = recordDao;
        this.channelProperties = channelProperties;
        this.commandLogService = commandLogService;
        //通道级超时：设备在"十公里外"，网络不可靠——发指令不能无限等（指令超时是反馈闭环的前置）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public void open(String deviceNo) {
        sendManual(deviceNo, "OPEN");
    }

    @Override
    public void close(String deviceNo) {
        sendManual(deviceNo, "CLOSE");
    }

    @Override
    public void sendByRule(String deviceNo, String action) {
        long seq = commandLogService.nextSeq(deviceNo);
        DeviceCommandLogEntity cmdLog =
                commandLogService.recordPending(deviceNo, action, seq, TriggerType.AUTO_RULE);
        try {
            dispatch(deviceNo, action, seq);
            log.info("自动校正指令已下发 deviceNo={} action={} seq={} -> 等待设备事件回报", deviceNo, action, seq);
        } catch (RRException e) {
            commandLogService.markSendFailed(cmdLog.getCommandId());
            //自动任务不因单台设备失败中断：记账+日志，下一轮对账自然重试（设备恢复在线即校正）
            log.warn("自动校正指令下发失败 deviceNo={} action={} cause={}", deviceNo, action, e.getMessage());
        }
    }

    @Override
    public void retryPending(DeviceCommandLogEntity pendingLog) {
        try {
            //重发同 seq：设备侧幂等（已执行过则忽略回执，真没收到则执行）——重试不会导致重复动作
            dispatch(pendingLog.getDeviceNo(), pendingLog.getCommandAction(), pendingLog.getCommandSeq());
            log.info("超时重试已下发 deviceNo={} action={} seq={} retryCount={}",
                    pendingLog.getDeviceNo(), pendingLog.getCommandAction(),
                    pendingLog.getCommandSeq(), pendingLog.getRetryCount() + 1);
        } catch (RRException e) {
            log.warn("超时重试下发失败 deviceNo={} seq={} cause={}",
                    pendingLog.getDeviceNo(), pendingLog.getCommandSeq(), e.getMessage());
        } finally {
            //无论下发成败都计数：设备持续无响应时靠计数触达上限转告警，防无限重试
            commandLogService.markRetried(pendingLog.getCommandId());
        }
    }

    /**
     * 管理端手动下发：失败当场抛错（人在等结果），流水记 SEND_FAILED
     */
    private void sendManual(String deviceNo, String action) {
        //档案必须存在：对未登记的设备发指令 = 对着空气说话
        DeviceRecordEntity record = recordDao.selectOne(
                new LambdaQueryWrapper<DeviceRecordEntity>()
                        .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
        if (record == null) {
            throw new RRException("设备未登记: " + deviceNo);
        }

        long seq = commandLogService.nextSeq(deviceNo);
        DeviceCommandLogEntity cmdLog =
                commandLogService.recordPending(deviceNo, action, seq, TriggerType.MANUAL);
        try {
            dispatch(deviceNo, action, seq);
            //指令已受理：不改台账状态（等设备事件回来再更新——反馈闭环铁律）
            log.info("手动指令已下发 deviceNo={} action={} seq={} -> 等待设备事件回报", deviceNo, action, seq);
        } catch (RRException e) {
            commandLogService.markSendFailed(cmdLog.getCommandId());
            throw e;
        }
    }

    /**
     * HTTP 下发（携 seq 幂等键）；设备拒绝/网络失败统一抛 RRException，由触发源决定失败语义
     */
    private void dispatch(String deviceNo, String action, long seq) {
        String url = channelProperties.getSimBaseUrl() + CMD_PATH;
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceNo", deviceNo);
        payload.put("action", action);
        payload.put("commandSeq", seq);
        try {
            Map<?, ?> resp = restTemplate.postForObject(url, payload, Map.class);
            Object code = resp == null ? null : resp.get("code");
            if (resp == null || !Integer.valueOf(0).equals(code)) {
                Object msg = resp == null ? "空响应" : resp.get("msg");
                throw new RRException("设备拒绝指令: " + msg);
            }
        } catch (RestClientException e) {
            //网络层失败：设备离线/不可达，显式失败不留假状态
            throw new RRException("设备无响应(连接失败): " + deviceNo + " cause=" + e.getMessage());
        }
    }
}
