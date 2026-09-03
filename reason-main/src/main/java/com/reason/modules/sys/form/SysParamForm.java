package com.reason.modules.sys.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "参数查询对象")
@Data
public class SysParamForm extends CommonForm {
    @Schema(description = "参数名称")
    private String paramName;
}
