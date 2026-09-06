package com.reason.modules.sys.vo;

import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "重置密码VO")
@Data
public class SysPasswordVO {
    @Schema(description = "用户ID")
    @NotNull(message = "用户主键不能为空")
    private Long userId;        //用户ID

    @Schema(description = "用户密码")
    @NotBlank(message="密码不能为空")
    private String password;    //密码

}
