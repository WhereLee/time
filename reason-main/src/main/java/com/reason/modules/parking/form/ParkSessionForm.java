package com.reason.modules.parking.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 停车会话分页查询条件（管理端只读）
 *
 * @date 2026-09-05
 */
@Schema(description = "停车会话分页查询条件")
@Data
@EqualsAndHashCode(callSuper = true)
public class ParkSessionForm extends CommonForm {

    @Schema(description = "车位编号（模糊）")
    private String spaceNo;
    @Schema(description = "车牌号（模糊，查询端不大写约束——存储恒为大写）")
    private String plateNo;
    @Schema(description = "状态：0-进行中 1-已结束 2-已取消（空=全部）")
    private Integer sessionState;
}
