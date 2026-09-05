package com.reason.modules.device.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 闸机人工操作留痕分页查询表单
 *
 * @date 2026-09-06
 */
@Schema(description = "闸机人工操作留痕分页查询表单")
@Data
@EqualsAndHashCode(callSuper = true)
public class GateManualOpForm extends CommonForm {

    @Schema(description = "设备编号（模糊）")
    private String deviceNo;
    @Schema(description = "车牌号（精确）")
    private String plateNo;
    @Schema(description = "指令结果（0-成功 1-设备不可达；空=全部）")
    private Integer opResult;
}
