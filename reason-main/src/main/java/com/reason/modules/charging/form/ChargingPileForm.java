package com.reason.modules.charging.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 充电桩分页查询条件
 *
 * @date 2026-09-05
 */
@Schema(description = "充电桩分页查询条件")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChargingPileForm extends CommonForm {

    @Schema(description = "桩编号（模糊）")
    private String pileNo;
    @Schema(description = "绑定车位 id（精确，空=全部）")
    private Long spaceId;
    @Schema(description = "状态：0-空闲 1-充电中 2-停用（空=全部）")
    private Integer pileState;
}
