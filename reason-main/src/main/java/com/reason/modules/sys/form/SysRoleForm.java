package com.reason.modules.sys.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "角色查询实体")
@Data
public class SysRoleForm extends CommonForm{
    @Schema(description = "角色名称")
    private String roleName;    //角色名称
    @Schema(description = "角色描述")
    private String roleComment; //角色描述
}
