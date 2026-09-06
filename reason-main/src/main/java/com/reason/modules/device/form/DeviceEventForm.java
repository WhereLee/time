package com.reason.modules.device.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备事件上报表单（设备侧 → 平台：状态变化通知）
 *
 * <p>只含"设备号 + 新状态码"；时间戳由平台盖（单一时钟源：设备时钟不可信，
 * 平台落库时间以服务器为准）。状态码语义与 DeviceState 一致：1-升起 2-降下 3-动作中 4-故障。</p>
 */
@Schema(description = "设备事件上报表单")
@Data
public class DeviceEventForm {

    @Schema(description = "设备编号")
    private String deviceNo;

    @Schema(description = "新状态码：1-升起 2-降下 3-动作中 4-故障")
    private Integer state;
}
