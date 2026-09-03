package com.reason.modules.sys.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 
 * 查询数据对象
 * @date 2021-02-20 15:06:27
 */
@Schema(description = "小程序用户查询对象")
@Data
public class SysUserWechatForm extends CommonForm {
    @Schema(description = "OPENID")
    private String wechatOpenid;

    @Schema(description = "昵称")
    private String wechatNickname;

    @Schema(description = "手机")
    private String wechatMobile;

    @Schema(description = "管理员用户名")
    private String userName;
}
