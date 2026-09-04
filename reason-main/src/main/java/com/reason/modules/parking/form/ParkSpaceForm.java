package com.reason.modules.parking.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车位分页查询条件
 *
 * @date 2026-09-05
 */
@Schema(description = "车位分页查询条件")
@Data
@EqualsAndHashCode(callSuper = true)
public class ParkSpaceForm extends CommonForm {

    @Schema(description = "车位编号（模糊）")
    private String spaceNo;
    @Schema(description = "区域/位置描述（模糊）")
    private String spaceArea;
    @Schema(description = "状态：0-空闲 1-占用 2-禁用（空=全部）")
    private Integer spaceState;
}
