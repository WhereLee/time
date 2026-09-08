package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Result;
import com.reason.modules.device.config.DeviceChannelAuthenticator;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceHeartbeatForm;
import com.reason.modules.device.service.DeviceMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 设备心跳接收口（设备侧 → 平台，周期性自述）
 *
 * <p>与事件通道同为设备侧调用（无管理端登录态），走 0.5 per-device HMAC 设备通道鉴权 +
 * SecurityConfig 白名单。心跳高频（10s/台），处理路径保持轻：对账 → 告警 → 刷 Redis TTL。</p>
 *
 * <p>state 语义（0.7）：允许 null（只报活不报态——老设备/降级模式），null 时跳过对账仅刷 TTL；
 * 非法码 400 语义化（疑似伪造/协议错）且不影响后续正常心跳。</p>
 *
 * <p>结构化日志（批次1）：心跳高频（10s/台），正常路径 debug 级防刷屏（dev 下 com.reason=DEBUG 可见）；
 * 拒绝路径 warn 级（伪造/配置错位必须立即可见）。</p>
 */
@Slf4j
@Tag(name = "设备心跳")
@RestController
@RequestMapping("device/heartbeat")
public class DeviceHeartbeatController {

    /** 设备编号请求头（0.5 per-device 凭证寻址） */
    private static final String DEVICE_HEADER = "X-Device-No";

    /** 设备签名请求头（HMAC(secret, deviceNo|state)） */
    private static final String SIGN_HEADER = "X-Device-Sign";

    @Autowired
    private DeviceMonitorService deviceMonitorService;

    @Autowired
    private DeviceChannelAuthenticator authenticator;

    /**
     * 心跳上报：刷新在线状态 + 状态自述对账（state 可空=只报活）
     */
    @Operation(summary = "心跳上报", description = "设备侧周期调用：刷新在线 key(TTL)，携带状态自述供对账校正（state 可空=只报活）")
    @ApiOperationSupport(order = 1)
    @PostMapping
    public Result<String> heartbeat(@RequestHeader(value = DEVICE_HEADER, required = false) String deviceNoHeader,
                                    @RequestHeader(value = SIGN_HEADER, required = false) String signature,
                                    @RequestBody DeviceHeartbeatForm form) {
        //批次1：心跳高频，正常路径 debug（排障时开 DEBUG 可看报文级到达记录，平时不刷屏）
        log.debug("[心跳入口] deviceNo={} state={}", form.getDeviceNo(), form.getState());
        //0.5 设备通道鉴权：per-device HMAC（与事件通道同一凭证体系）
        authenticator.authenticateHeartbeat(form.getDeviceNo(), form.getState(), signature);

        //0.7：state 允许 null（只报活）；非 null 必须合法（非法=协议错 400，疑似伪造）
        Integer stateCode = null;
        if (form.getState() != null) {
            try {
                stateCode = DeviceState.fromCode(form.getState()).getCode();
            } catch (IllegalArgumentException e) {
                log.warn("[心跳入口-拒绝] 非法状态码 deviceNo={} state={}", form.getDeviceNo(), form.getState());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "非法状态码: " + form.getState() + "（协议 v2：1-升起 2-降下 3-动作中 4-故障）");
            }
        }
        try {
            deviceMonitorService.heartbeat(form.getDeviceNo(), stateCode);
        } catch (RRException e) {
            //未登记设备心跳 = 配置错位（调用方问题）-> 400
            log.warn("[心跳入口-拒绝] 业务拒绝 deviceNo={} cause={}", form.getDeviceNo(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return Result.ok();
    }
}
