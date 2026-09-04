package com.reason.device.reporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.reason.device.config.DeviceSimProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 停车事件上报客户端（device-sim → reason-main /device/parking/*）
 *
 * <p>携 X-Device-Token 走设备通道（与 main 侧 DeviceAuthFilter 对应）。
 * 上报失败抛异常：由调用方（手控/剧本）捕获记日志——设备重试语义真实存在，
 * 由 M2 心跳/离线补偿治理，M0 仅记录失败现场。</p>
 */
@Slf4j
@Component
public class ParkingEventReporter {

    private final DeviceSimProperties properties;
    private final RestClient restClient;

    public ParkingEventReporter(DeviceSimProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 入场上报：返回会话 id（设备持有句柄供出场/取消使用）
     */
    public Long reportEntry(String spaceNo, String plateNo) {
        JsonNode resp = post("/device/parking/entry", Map.of("spaceNo", spaceNo, "plateNo", plateNo));
        return extractDataId(resp, "入场上报");
    }

    /**
     * 出场上报：返回订单 id
     */
    public Long reportExit(String deviceNo, String spaceNo, Long sessionId) {
        JsonNode resp = post("/device/parking/exit",
                Map.of("deviceNo", deviceNo, "spaceNo", spaceNo, "sessionId", sessionId));
        return extractDataId(resp, "出场上报");
    }

    /**
     * 取消上报
     */
    public void reportCancel(String deviceNo, Long sessionId, String reason) {
        post("/device/parking/cancel",
                Map.of("deviceNo", deviceNo, "sessionId", sessionId, "cancelReason", reason));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String url = properties.getParkingApiBaseUrl() + path;
        JsonNode resp = restClient.post().uri(url)
                .header("X-Device-Token", properties.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (resp == null || resp.path("code").asInt(0) != 0) {
            throw new IllegalStateException("业务端返回异常：" + (resp == null ? "null" : resp.toString()));
        }
        return resp;
    }

    /** 提取 data 中的 id 字段（业务端 code=0 时 data 为对象或标量） */
    private Long extractDataId(JsonNode resp, String action) {
        JsonNode data = resp.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException(action + "响应缺 data：" + resp);
        }
        JsonNode id = data.isObject() ? data.path("orderId") : data;
        if (id.isMissingNode() || !id.canConvertToLong()) {
            throw new IllegalStateException(action + "响应 data 异常：" + resp);
        }
        return id.asLong();
    }
}
