package com.reason.modules.parking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 设备控制指令下发客户端（reason-main → device-sim /cmd/exec）
 *
 * <p>一致性语义：账务正确性不依赖设备反馈——出场结算事务已完成，
 * 放行指令失败只告警（设备失联由 M2 指令重试/告警治理接管），不向上抛。</p>
 */
@Slf4j
@Service("deviceCommandClient")
public class DeviceCommandClient {

    /** 控制指令：开闸放行 */
    public static final String CMD_OPEN_GATE = "OPEN_GATE";

    @Value("${reason.device.sim-base-url:http://127.0.0.1:8300}")
    private String simBaseUrl;

    private final RestClient restClient;

    public DeviceCommandClient() {
        //设备通道同步调用有界超时：不给主请求留长尾
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("")
                .build();
    }

    /**
     * 下发控制指令（成功返回 true；网络/超时/非 2xx 一律告警不抛）
     *
     * @param spaceNo 目标车位编号
     */
    public boolean sendCommand(String cmd, String spaceNo) {
        try {
            String url = simBaseUrl + "/cmd/exec";
            restClient.post().uri(url)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("cmd", cmd, "spaceNo", spaceNo,
                            "ts", System.currentTimeMillis() / 1000))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            //失败不抛：账务已完成，指令可重试性归 M2 治理
            log.warn("设备控制指令下发失败（不影响账务）：cmd={}, spaceNo={}, err={}",
                    cmd, spaceNo, e.getMessage());
            return false;
        }
    }
}
