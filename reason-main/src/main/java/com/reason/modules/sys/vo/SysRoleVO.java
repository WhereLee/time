package com.reason.modules.sys.vo;

import com.reason.common.utils.StringUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "角色VO")
@Data
public class SysRoleVO {
    @Schema(description = "角色ID")
    @NotNull(message = "角色主键不能为空",groups = {UpdateGroup.class})
    private Long roleId;        //角色ID

    @Schema(description = "角色名称")
    @Size(max = 64,message = "角色名称过长",groups = {AddGroup.class,UpdateGroup.class})
    @NotBlank(message="角色名称不能为空",groups = {AddGroup.class})
    private String roleName;    //角色名称

    public void setRoleName(String roleName) {
        this.roleName = StringUtils.replaceBlank(roleName);
    }

    @Schema(description = "角色描述")
    @Size(max = 256,message = "角色描述过长",groups = {AddGroup.class,UpdateGroup.class})
    private String roleComment;     //角色描述，备注

    public void setRoleComment(String roleComment) {
        this.roleComment = StringUtils.replaceBlank(roleComment);
    }

    @Schema(description = "菜单ID列表")
    private List<Long> menuIdList;      //菜单ID列表
}
