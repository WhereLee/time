package com.reason.modules.device.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备指令表单（管理端对设备下发远程动作）
 *
 * <p>动作合法性（该状态能否升/降）由设备侧裁决——平台不保存设备真实状态，
 * 台账只是快照；发指令只带目标设备编号，不猜状态。</p>
 */
@Schema(description = "设备指令表单")
@Data
public class DeviceCommandForm {

    @Schema(description = "设备编号（必填）")
    private String deviceNo;
}
