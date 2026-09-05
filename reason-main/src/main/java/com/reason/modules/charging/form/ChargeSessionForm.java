package com.reason.modules.charging.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 充电会话分页查询条件
 *
 * @date 2026-09-05
 */
@Schema(description = "充电会话分页查询条件")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChargeSessionForm extends CommonForm {

    @Schema(description = "桩编号（模糊）")
    private String pileNo;
    @Schema(description = "车牌号（模糊）")
    private String plateNo;
    @Schema(description = "状态：0-充电中 1-已结束 2-已取消 3-超时结束（空=全部）")
    private Integer sessionState;
}
