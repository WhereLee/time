package com.reason.modules.device.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备指令流水查询表单（管理端）
 */
@Schema(description = "设备指令流水查询表单")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceCommandLogForm extends CommonForm {

    @Schema(description = "设备编号（精确）")
    private String deviceNo;

    @Schema(description = "指令动作：OPEN/CLOSE")
    private String commandAction;

    @Schema(description = "触发源：1-管理端手动 2-自动规则 3-超时重试")
    private Integer triggerType;

    @Schema(description = "状态：0-已下发待到位 1-已到位 2-下发失败 3-重试超限转故障 4-执行失败(设备故障)")
    private Integer commandStatus;
}
