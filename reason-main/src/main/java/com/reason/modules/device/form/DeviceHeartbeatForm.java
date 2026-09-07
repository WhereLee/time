package com.reason.modules.device.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备心跳表单（设备侧 → 平台，周期性自述）
 *
 * <p>心跳 = "我还活着 + 我现在是什么状态"。状态自述是对账的数据源：
 * 与台账快照不一致时以设备为准校正（外力改态/重启漂移/事件丢失都在此被吸收）。</p>
 */
@Schema(description = "设备心跳表单")
@Data
public class DeviceHeartbeatForm {

    @Schema(description = "设备编号")
    private String deviceNo;

    @Schema(description = "当前状态自述：1-升起 2-降下 3-动作中 4-故障")
    private Integer state;
}
