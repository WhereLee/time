/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录表单
 *
 * @author Mark sunlightcs@gmail.com
 */
@Schema(description = "登录实体")
@Data
public class SysLoginForm {
    @Schema(description = "登录名")
    private String loginname;

    @Schema(description = "登录密码")
    private String password;

    //@Schema(description = "手机号码")
    //private String mobile;

    @Schema(description = "验证码")
    private String code;
    //private String captcha;
    //private String uuid;


}
