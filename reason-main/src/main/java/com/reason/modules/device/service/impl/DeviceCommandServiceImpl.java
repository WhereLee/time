package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.exception.RRException;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.config.DeviceSignature;
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

    /** 状态查询端点相对路径（协议 v2：/cmd/query，T20 QUERY_STATE——非动作不进流水） */
    private static final String QUERY_PATH = "/cmd/query";

    /** 设备签名请求头（0.5 per-device HMAC：X-Device-Sign=HMAC(secret, deviceNo|action|seq)） */
    private static final String SIGN_HEADER = "X-Device-Sign";

    private static final String NO_DEVICE_HEADER = "X-Device-No";

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
        boolean rejected = false;
        try {
            //重发同 seq：设备侧幂等（已执行过则忽略回执，真没收到则执行）——重试不会导致重复动作
            dispatch(pendingLog.getDeviceNo(), pendingLog.getCommandAction(), pendingLog.getCommandSeq());
            log.info("超时重试已下发 deviceNo={} action={} seq={} retryCount={}",
                    pendingLog.getDeviceNo(), pendingLog.getCommandAction(),
                    pendingLog.getCommandSeq(), pendingLog.getRetryCount() + 1);
        } catch (RRException e) {
            if (e.getMessage() != null && e.getMessage().contains("拒绝")) {
                //0.6：设备明确拒绝（状态不合法/曾被拒 seq 重试）——重试无意义，终止为 SEND_FAILED
                //（设备拒绝是"明确答案"不是链路抖动，继续烧重试次数只会误告警 RETRY_EXCEEDED）
                rejected = true;
                commandLogService.markSendFailed(pendingLog.getCommandId());
                log.warn("超时重试被设备拒绝 -> 流水终止 SEND_FAILED deviceNo={} seq={} cause={}",
                        pendingLog.getDeviceNo(), pendingLog.getCommandSeq(), e.getMessage());
            } else {
                log.warn("超时重试下发失败 deviceNo={} seq={} cause={}",
                        pendingLog.getDeviceNo(), pendingLog.getCommandSeq(), e.getMessage());
            }
        } finally {
            //网络层失败仍计数（防无限重试）；设备拒绝不计（已终止）
            if (!rejected) {
                commandLogService.markRetried(pendingLog.getCommandId());
            }
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
     * HTTP 下发（携 seq 幂等键 + 0.5 per-device HMAC 签名头）；设备拒绝/网络失败统一抛
     * RRException（"拒绝"前缀=设备明确拒绝，"设备无响应"=链路故障——调用方按语义分派）
     */
    private void dispatch(String deviceNo, String action, long seq) {
        String url = channelProperties.getSimBaseUrl() + CMD_PATH;
        String secret = querySecret(deviceNo);
        if (secret == null) {
            throw new RRException("设备密钥缺失，拒绝下发: " + deviceNo);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceNo", deviceNo);
        payload.put("action", action);
        payload.put("commandSeq", seq);
        try {
            //签名头：HMAC(secret, deviceNo|action|seq)——sim 校验平台身份（下行不再是"裸奔"，T3）
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(NO_DEVICE_HEADER, deviceNo);
            headers.set(SIGN_HEADER, DeviceSignature.sign(secret, DeviceSignature.canonicalCommand(deviceNo, action, seq)));
            Map<?, ?> resp = restTemplate.postForObject(url,
                    new org.springframework.http.HttpEntity<>(payload, headers), Map.class);
            Object code = resp == null ? null : resp.get("code");
            if (resp == null || !Integer.valueOf(0).equals(code)) {
                Object msg = resp == null ? "空响应" : resp.get("msg");
                throw new RRException("设备拒绝指令: " + msg);
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            //0.7：sim 4xx（401 验签失败/400 协议错）= 设备明确拒绝——显式失败，不再归为"无响应"
            throw new RRException("设备拒绝指令(HTTP " + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            //网络层失败：设备离线/不可达，显式失败不留假状态
            throw new RRException("设备无响应(连接失败): " + deviceNo + " cause=" + e.getMessage());
        }
    }

    /**
     * 取设备 HMAC 密钥（0.5 per-device 凭证）：下行指令与状态查询共用的签名密钥
     *
     * @return secret；null=档案不存在或未配置密钥（fail secure：密钥缺失拒绝下发）
     */
    private String querySecret(String deviceNo) {
        DeviceRecordEntity record = recordDao.selectOne(new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
        if (record == null || record.getDeviceSecret() == null || record.getDeviceSecret().isEmpty()) {
            return null;
        }
        return record.getDeviceSecret();
    }

    @Override
    public QueryResult queryState(String deviceNo) {
        //QUERY_STATE（协议 v2 §2.2）：主动问设备实况——用于监控对账与上行故障诊断（T20）；
        //查询失败（设备无响应/被拒/应答缺字段）返回 null，与"查到非目标态"区分（调用方决策依据不同）
        String url = channelProperties.getSimBaseUrl() + QUERY_PATH;
        String secret = querySecret(deviceNo);
        if (secret == null) {
            log.warn("状态查询取消：设备密钥缺失 deviceNo={}", deviceNo);
            return null;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("deviceNo", deviceNo);
            //签名头（0.5）：sim 校验平台身份
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(NO_DEVICE_HEADER, deviceNo);
            headers.set(SIGN_HEADER, DeviceSignature.sign(secret, DeviceSignature.canonicalCommand(deviceNo, "QUERY", 0)));
            Map<?, ?> resp = restTemplate.postForObject(url,
                    new org.springframework.http.HttpEntity<>(payload, headers), Map.class);
            if (resp == null || !Integer.valueOf(0).equals(resp.get("code"))) {
                log.warn("状态查询被拒 deviceNo={} resp={}", deviceNo, resp);
                return null;
            }
            Object data = resp.get("data");
            if (!(data instanceof Map<?, ?> m) || m.get("state") == null) {
                log.warn("状态查询应答缺 data/state deviceNo={} data={}", deviceNo, data);
                return null;
            }
            QueryResult result = new QueryResult();
            result.setDeviceNo(m.get("deviceNo") == null ? null : String.valueOf(m.get("deviceNo")));
            result.setState(((Number) m.get("state")).intValue());
            result.setBootId(m.get("bootId") == null ? null : String.valueOf(m.get("bootId")));
            result.setEventSeq(m.get("eventSeq") == null ? null : ((Number) m.get("eventSeq")).longValue());
            result.setLastCommandSeq(m.get("lastCommandSeq") == null
                    ? null : ((Number) m.get("lastCommandSeq")).longValue());
            log.info("状态查询成功 deviceNo={} state={} bootId={} eventSeq={} lastCommandSeq={}",
                    deviceNo, result.getState(), result.getBootId(),
                    result.getEventSeq(), result.getLastCommandSeq());
            return result;
        } catch (RestClientException e) {
            //网络层失败：设备离线/不可达——返回 null 让监控任务走重试/超限路径
            log.warn("状态查询失败(设备无响应) deviceNo={} cause={}", deviceNo, e.getMessage());
            return null;
        }
    }
}
