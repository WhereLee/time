package com.reason.modules.device.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备告警查询表单（管理端）
 */
@Schema(description = "设备告警查询表单")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceAlarmForm extends CommonForm {

    @Schema(description = "设备编号（精确）")
    private String deviceNo;

    @Schema(description = "告警类型：1-指令重试超限 2-设备离线 3-状态对账不一致 4-设备故障上报")
    private Integer alarmType;

    @Schema(description = "处理状态：0-未处理 1-已处理")
    private Integer alarmHandled;
}
