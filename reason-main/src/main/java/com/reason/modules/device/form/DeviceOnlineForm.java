package com.reason.modules.device.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备台账分页查询表单
 *
 * @date 2026-09-06
 */
@Schema(description = "设备台账分页查询表单")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceOnlineForm extends CommonForm {

    @Schema(description = "设备编号（模糊）")
    private String deviceNo;
    @Schema(description = "设备类型（0-入口闸机 1-出口闸机 2-位检 3-充电桩；空=全部）")
    private Integer deviceType;
    @Schema(description = "在线态（0-离线 1-在线；空=全部）")
    private Integer deviceState;
    @Schema(description = "绑定对象（出入口编码/车位号/桩号，模糊）")
    private String bindTarget;
}
