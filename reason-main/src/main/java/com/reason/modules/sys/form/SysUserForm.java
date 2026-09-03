package com.reason.modules.sys.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户查询对象")
@Data
public class SysUserForm extends CommonForm{
    @Schema(description = "用户姓名")
    private String userName;
    @Schema(description = "电话号码")
    private String userPhone;
}
