package com.reason.modules.sys.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "系统日志查询条件")
@Data
public class SysLogForm extends CommonForm{
    @Schema(description = "日志类型 1-WEB端 2-APP端")
    private Integer logType;

    @Schema(description = "模块")
    private String logModule;       //模块

    @Schema(description = "功能")
    private String logFunc;         //功能

    @Schema(description = "执行结果 0-成功 1-失败")
    private Integer logState;       //执行结果 0-成功 1-失败

    @Schema(description = "参数")
    private String logParams;       //参数

    @Schema(description = "操作人")
    private String logCreatorName;  //操作人

    @Schema(description = "OPENID")
    private String logOpenid;

    @Schema(description = "昵称")
    private String logNickname;

    @Schema(description = "手机")
    private String logMobile;

    @Schema(description = "起始时间戳，单位秒")
    private Long starttime;         //操作起始时间

    @Schema(description = "截止时间戳，单位秒")
    private Long endtime;           //操作截止时间
}
