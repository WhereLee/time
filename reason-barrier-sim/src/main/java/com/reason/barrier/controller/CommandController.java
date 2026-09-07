package com.reason.barrier.controller;

import com.reason.barrier.config.DeviceSignature;
import com.reason.barrier.config.SimProperties;
import com.reason.barrier.network.NetworkCondition;
import com.reason.barrier.service.DeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 指令接收口（平台 -> 设备）——只做 HTTP 契约翻译，业务在 DeviceService
 *
 * <p>契约（与平台 DeviceCommandServiceImpl 一致）：POST /cmd
 * body {"deviceNo": "...", "action": "OPEN|CLOSE", "commandSeq": 123}；
 * 回执 code=0 = 指令已受理（含幂等忽略——设备保证不重复动作），
 * code=1 = 设备拒绝（动作不合法/防砸互锁/故障态/设备不存在/缺 seq），msg=原因。
 * 状态查询（协议 v2 §2.2）：POST /cmd/query body {"deviceNo"}；
 * 回执 code=0 + data=设备实况快照（state/bootId/eventSeq/lastCommandSeq）。</p>
 *
 * <p>网络剧本：下行方向断（blockDownstream）时本口拒绝一切入站（503）——
 * 模拟平台连不上设备的链路故障（平台侧表现为"设备无响应"→ SEND_FAILED/重试路径）。</p>
 */
@RestController
public class CommandController {

    private final DeviceService deviceService;
    private final NetworkCondition network;
    private final SimProperties properties;

    /** 平台身份签名请求头（0.5：下行验签——X-Device-Sign=HMAC(secret, deviceNo|action|seq)） */
    private static final String SIGN_HEADER = "X-Device-Sign";

    public CommandController(DeviceService deviceService, NetworkCondition network, SimProperties properties) {
        this.deviceService = deviceService;
        this.network = network;
        this.properties = properties;
    }

    @PostMapping("/cmd")
    public Map<String, Object> cmd(@RequestHeader(value = SIGN_HEADER, required = false) String signature,
                                   @RequestBody Map<String, Object> req) {
        //网络剧本：下行方向断——入站指令整体拒绝（平台 dispatch 收 5xx -> "设备无响应"）
        if (network.isBlockDownstream()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "设备不可达(网络剧本: 下行阻断)");
        }

        String deviceNo = asString(req.get("deviceNo"));
        String action = asString(req.get("action"));
        //0.7 协议垃圾语义化：字段类型错/缺失 -> 400（不再 ClassCastException 500）
        if (deviceNo == null || action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺 deviceNo/action");
        }
        Object seqObj = req.get("commandSeq");
        //seq 是幂等契约的必要字段：缺失直接拒绝（无 seq 指令无法去重，宁可拒收）
        if (seqObj == null) {
            return Map.of("code", 1, "msg", "缺少指令序号 commandSeq");
        }
        long seq;
        try {
            seq = Long.parseLong(String.valueOf(seqObj));
        } catch (NumberFormatException e) {
            return Map.of("code", 1, "msg", "commandSeq 非法: " + seqObj);
        }

        //0.5 下行验签：平台必须持设备 secret 签名（T3：控制面不再是"裸奔"，直控指令被拒）
        authenticatePlatform(deviceNo, action, seq, signature);

        try {
            String msg;
            if ("OPEN".equals(action)) {
                msg = deviceService.open(deviceNo, seq);
            } else if ("CLOSE".equals(action)) {
                msg = deviceService.close(deviceNo, seq);
            } else {
                return Map.of("code", 1, "msg", "未知动作: " + action);
            }
            return Map.of("code", 0, "msg", msg);
        } catch (IllegalStateException e) {
            //设备拒绝动作（状态机裁决/防砸互锁/故障态/曾被拒 seq）——显式回执，平台记 SEND_FAILED
            return Map.of("code", 1, "msg", e.getMessage());
        } catch (IllegalArgumentException e) {
            //设备不存在：平台向未安装的设备发指令
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }

    /**
     * 状态查询（QUERY_STATE，协议 v2 §2.2——T20：平台主动拿设备实况，替代查自己写的台账快照）
     */
    @PostMapping("/cmd/query")
    public Map<String, Object> query(@RequestHeader(value = SIGN_HEADER, required = false) String signature,
                                     @RequestBody Map<String, Object> req) {
        //网络剧本：下行方向断——查询同样不可达（平台 queryState 收 5xx -> null -> 重试路径）
        if (network.isBlockDownstream()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "设备不可达(网络剧本: 下行阻断)");
        }
        String deviceNo = req.get("deviceNo") == null ? null : String.valueOf(req.get("deviceNo"));
        if (deviceNo == null || deviceNo.isBlank()) {
            return Map.of("code", 1, "msg", "缺少设备编号 deviceNo");
        }
        //0.5：查询同样要求平台签名（下行通道同权鉴权）
        authenticatePlatform(deviceNo, "QUERY", 0, signature);
        try {
            Map<String, Object> snapshot = new HashMap<>(deviceService.queryState(deviceNo));
            return Map.of("code", 0, "data", snapshot);
        } catch (IllegalArgumentException e) {
            return Map.of("code", 1, "msg", e.getMessage());
        }
    }

    /**
     * 下行鉴权（0.5）：平台必须持该设备 secret 签名；验签失败 401（平台 dispatch 收 4xx 判"拒绝"）
     */
    private void authenticatePlatform(String deviceNo, String action, long seq, String signature) {
        String secret = properties.secretOf(deviceNo);
        if (secret == null || secret.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "设备未配置密钥(联调需环境变量注入): " + deviceNo);
        }
        if (!DeviceSignature.verify(secret, DeviceSignature.canonicalCommand(deviceNo, action, seq), signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "平台签名无效: " + deviceNo);
        }
    }

    private String asString(Object o) {
        return o instanceof String s ? s : null;
    }
}
