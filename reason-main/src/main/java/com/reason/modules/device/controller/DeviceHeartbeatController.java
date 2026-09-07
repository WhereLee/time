package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Result;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceHeartbeatForm;
import com.reason.modules.device.service.DeviceMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备心跳接收口（设备侧 → 平台，周期性自述）
 *
 * <p>与事件通道同为设备侧调用（无管理端登录态），走 X-Device-Token 设备通道鉴权 +
 * SecurityConfig 白名单。心跳高频（10s/台），处理路径保持轻：对账 → 告警 → 刷 Redis TTL。</p>
 */
@Tag(name = "设备心跳")
@RestController
@RequestMapping("device/heartbeat")
public class DeviceHeartbeatController {

    /** 设备通道令牌请求头（与事件通道同一凭证体系） */
    private static final String TOKEN_HEADER = "X-Device-Token";

    @Autowired
    private DeviceMonitorService deviceMonitorService;

    @Autowired
    private DeviceChannelProperties channelProperties;

    /**
     * 心跳上报：刷新在线状态 + 状态自述对账
     */
    @Operation(summary = "心跳上报", description = "设备侧周期调用：刷新在线 key(TTL)，携带状态自述供对账校正")
    @ApiOperationSupport(order = 1)
    @PostMapping
    public Result<String> heartbeat(@RequestHeader(TOKEN_HEADER) String token,
                                    @RequestBody DeviceHeartbeatForm form) {
        //设备通道鉴权（与事件通道一致：常量时间比对收口在 DeviceChannelProperties.matches，快速失败不静默）
        if (!channelProperties.matches(token)) {
            throw new RRException("设备令牌无效");
        }
        //状态码合法性校验（未知码=协议错，快速失败；心跳必须带状态——自述是对账数据源）
        DeviceState state = DeviceState.fromCode(form.getState());
        deviceMonitorService.heartbeat(form.getDeviceNo(), state.getCode());
        return Result.ok();
    }
}
