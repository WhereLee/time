package com.reason.modules.sys.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

@Schema(description = "公共查询条件")
@Data
public class CommonForm {

    @Schema(description = "当前页数 默认 1")
    private String page;
    @Schema(description = "每页显示数量 默认 10")
    private String limit;
    private String sqlFilter;//数据权限限制-前端不必关注
}
