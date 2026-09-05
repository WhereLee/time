package com.reason.modules.parking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备控制指令下发客户端（reason-main → device-sim /cmd/exec）
 *
 * <p>一条铁律：业务正确性不依赖设备反馈——指令失败只告警，设备侧治理（重试/告警）后续块管理。
 * 寻址方式兼容：spaceNo（车位设备，M1 出场放行）与 deviceNo（闸机等设备台账，A 块手动抬杆）。</p>
 */
@Slf4j
@Service("deviceCommandClient")
public class DeviceCommandClient {

    /** 控制指令：抬杆放行 */
    public static final String CMD_OPEN_GATE = "OPEN_GATE";

    @Value("${reason.device.sim-base-url:http://127.0.0.1:8300}")
    private String simBaseUrl;

    private final RestClient restClient;

    public DeviceCommandClient() {
        //设备通道同步调用短超时：指令为 fire-and-forget 语义，失败即降级
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("")
                .build();
    }

    /**
     * 下发车位指令（按绑定车位号寻址，M1 出场放行语义）
     *
     * @param cmd     指令（CMD_OPEN_GATE 等）
     * @param spaceNo 目标车位编号
     * @return 是否下发成功（成功 true；超时/异常/非 2xx 一律告警返回 false）
     */
    public boolean sendCommand(String cmd, String spaceNo) {
        return doSend(cmd, null, spaceNo);
    }

    /**
     * 下发设备指令（按设备号寻址，A 块手动抬杆等管理端操作语义）
     *
     * @param cmd      指令（CMD_OPEN_GATE 等）
     * @param deviceNo 目标设备号（device_online.device_no，如 GATE-E-OUT）
     * @return 是否下发成功（失败不抛：调用方负责留痕与提示）
     */
    public boolean sendDeviceCommand(String cmd, String deviceNo) {
        return doSend(cmd, deviceNo, null);
    }

    private boolean doSend(String cmd, String deviceNo, String spaceNo) {
        try {
            String url = simBaseUrl + "/cmd/exec";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("cmd", cmd);
            body.put("ts", System.currentTimeMillis() / 1000);
            if (deviceNo != null) {
                body.put("deviceNo", deviceNo);
            }
            if (spaceNo != null) {
                body.put("spaceNo", spaceNo);
            }
            restClient.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            //失败不抛（不阻断业务事务）：指令为后置动作，设备不可达由调用方留痕/人工处理
            log.warn("设备控制指令下发失败（不影响业务事务）：cmd={}, deviceNo={}, spaceNo={}, err={}",
                    cmd, deviceNo, spaceNo, e.getMessage());
            return false;
        }
    }
}
