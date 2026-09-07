package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Result;
import com.reason.modules.device.config.DeviceChannelAuthenticator;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceEventForm;
import com.reason.modules.device.service.DeviceEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 设备事件接收口（设备侧 → 平台，协议 v2）
 *
 * <p>这是"反馈闭环"的平台入口：设备主动上报状态变化，平台据此更新台账——
 * 台账状态唯一合法的写入路径（带序守卫）。<b>本接口不是管理端操作</b>（无 @SysLog/无操作人），
 * 走设备通道鉴权（0.5 per-device HMAC：X-Device-No + X-Device-Sign，SecurityConfig 白名单放行）。</p>
 *
 * <p>错误语义（0.7）：鉴权失败 401、协议错误（非法状态码/未登记设备）400——设备通道的错误
 * 本质是"请求本身有问题"，不再 200+code500 让设备侧自省窗口失明。</p>
 */
@Tag(name = "设备事件")
@RestController
@RequestMapping("device/event")
public class DeviceEventController {

    /** 设备编号请求头（0.5 per-device 凭证寻址） */
    private static final String DEVICE_HEADER = "X-Device-No";

    /** 设备签名请求头（HMAC(secret, canonical)，协议 v2 §3.1 签名域） */
    private static final String SIGN_HEADER = "X-Device-Sign";

    @Autowired
    private DeviceEventService deviceEventService;

    @Autowired
    private DeviceChannelAuthenticator authenticator;

    /**
     * 设备状态上报（协议 v2）：序守卫更新台账 + 证据驱动销账流水
     */
    @Operation(summary = "状态上报", description = "设备侧调用：上报状态变化（协议v2：commandSeq/bootId/eventSeq），平台带序守卫更新台账")
    @ApiOperationSupport(order = 1)
    @PostMapping
    public Result<String> report(@RequestHeader(value = DEVICE_HEADER, required = false) String deviceNoHeader,
                                 @RequestHeader(value = SIGN_HEADER, required = false) String signature,
                                 @RequestBody DeviceEventForm form) {
        //0.5 设备通道鉴权：per-device HMAC（共享口令已退役）——伪造/重放由密钥+序守卫双层拦截
        authenticator.authenticateEvent(form.getDeviceNo(), form.getState(), form.getCommandSeq(),
                form.getBootId(), form.getEventSeq(), signature);
        //0.7 协议错误语义化：非法状态码 = 调用方问题 -> 400（不再是通用 500）
        try {
            DeviceState.fromCode(form.getState());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "非法状态码: " + form.getState() + "（协议 v2：1-升起 2-降下 3-动作中 4-故障）");
        }
        try {
            //事件语义编排（序守卫+证据驱动销账，收口在 DeviceEventService）
            deviceEventService.handleStateEvent(form);
        } catch (RRException e) {
            //未登记设备上报 = 配置错位（调用方问题）-> 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return Result.ok();
    }
}
