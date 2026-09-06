package com.reason.modules.sys.vo;

import com.reason.common.utils.StringUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "用户VO")
@Data
public class SysUserVO {
    @Schema(description = "用户ID")
    @NotNull(message = "用户主键不能为空",groups = {UpdateGroup.class})
    private Long userId;        //用户ID

    @Schema(description = "用户名称")
    @Size(max = 64,message = "用户名过长",groups = {AddGroup.class,UpdateGroup.class})
    @NotBlank(message="用户名不能为空",groups = {AddGroup.class})
    private String userName;    //用户名

    public void setUserName(String userName) {
        this.userName = StringUtils.replaceBlank(userName);
    }

    @Schema(description = "用户密码")
    @NotBlank(message="密码不能为空",groups = {AddGroup.class})
    private String userPassword;    //密码

    @Schema(description = "用户真实姓名")
    @Size(max = 64,message = "用户真实姓名过长",groups = {AddGroup.class,UpdateGroup.class})
    private String userRealname;    //用户真实姓名

    public void setUserRealname(String userRealname) {
        this.userRealname = StringUtils.replaceBlank(userRealname);
    }

    @Schema(description = "电话号码")
    @Size(max = 256,message = "电话号码过长",groups = {AddGroup.class,UpdateGroup.class})
    private String userPhone;       //电话号码

    public void setUserPhone(String userPhone) {
        this.userPhone = StringUtils.replaceBlank(userPhone);
    }

    @Schema(description = "企业微信用户ID")
    private String userQyweixinId;

    public void setUserQyweixinId(String userQyweixinId) {
        this.userQyweixinId = StringUtils.replaceBlank(userQyweixinId);
    }

    @Schema(description = "邮箱")
    @Size(max = 128,message = "邮箱过长",groups = {AddGroup.class,UpdateGroup.class})
    @Email(message="邮箱格式不正确", groups = {AddGroup.class, UpdateGroup.class})
    private String userEmail;       //邮箱

    public void setUserEmail(String userEmail) {
        this.userEmail = StringUtils.replaceBlank(userEmail);
    }

    @Schema(description = "角色ID列表")
    private List<Long> roleIdList;      //角色列表
}
