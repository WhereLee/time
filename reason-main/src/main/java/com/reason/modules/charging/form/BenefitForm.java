package com.reason.modules.charging.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 免停权益分页查询条件
 *
 * @date 2026-09-05
 */
@Schema(description = "免停权益分页查询条件")
@Data
@EqualsAndHashCode(callSuper = true)
public class BenefitForm extends CommonForm {

    @Schema(description = "权益码（模糊）")
    private String benefitNo;
    @Schema(description = "车牌号（模糊）")
    private String plateNo;
    @Schema(description = "状态：0-可用 1-已核销 2-已过期（空=全部）")
    private Integer benefitState;
}
