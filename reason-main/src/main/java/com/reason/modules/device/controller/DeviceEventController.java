package com.reason.modules.device.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.exception.RRException;
import com.reason.common.utils.Result;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceEventForm;
import com.reason.modules.device.service.DeviceRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备事件接收口（设备侧 → 平台，块3：状态变化回报的落地）
 *
 * <p>这是"反馈闭环"的平台入口：设备主动上报状态变化，平台据此更新台账——
 * 台账状态唯一合法的写入路径。<b>本接口不是管理端操作</b>（无 @SysLog/无操作人），
 * 走设备通道鉴权（X-Device-Token，SecurityConfig 白名单放行）。</p>
 */
@Tag(name = "设备事件")
@RestController
@RequestMapping("device/event")
public class DeviceEventController {

    /** 设备通道令牌请求头（样例固定口令；生产形态为独立设备鉴权体系） */
    private static final String TOKEN_HEADER = "X-Device-Token";

    @Autowired
    private DeviceRecordService deviceRecordService;

    @Autowired
    private DeviceChannelProperties channelProperties;

    /**
     * 设备状态上报：更新台账状态快照
     */
    @Operation(summary = "状态上报", description = "设备侧调用：上报状态变化，平台更新台账（1-升起 2-降下 3-动作中 4-故障）")
    @ApiOperationSupport(order = 1)
    @PostMapping
    public Result<String> report(@RequestHeader(TOKEN_HEADER) String token,
                                 @RequestBody DeviceEventForm form) {
        //设备通道鉴权（样例：共享口令比对；快速失败不静默）
        if (!channelProperties.getAccessToken().equals(token)) {
            throw new RRException("设备令牌无效");
        }
        //状态码合法性校验（未知码=协议错，快速失败）
        DeviceState state = DeviceState.fromCode(form.getState());
        deviceRecordService.updateStateByEvent(form.getDeviceNo(), state.getCode());
        return Result.ok();
    }
}
