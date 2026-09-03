package com.reason.modules.sys.vo;

import com.reason.common.utils.StringUtils;
import com.reason.common.validator.BlankOrPattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Size;

@Schema(description = "IP黑白名单VO")
@Data
public class SysDicIplistVO {
    @Schema(description = "白名单列表 英文逗号分隔")
    @BlankOrPattern(regexp = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])(\\.(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)){3}(,(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])(\\.(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)){3})*$"
            ,message = "白名单列表格式错误")
    @Size(max = 2048,message = "白名单列表过长")
    private String whiteList;

    public void setWhiteList(String whiteList) {
        this.whiteList = StringUtils.replaceBlank(whiteList);
    }

    @Schema(description = "黑名单列表 英文逗号分隔")
    @BlankOrPattern(regexp = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])(\\.(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)){3}(,(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])(\\.(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)){3})*$"
            ,message = "黑名单列表格式错误")
    @Size(max = 2048,message = "黑名单列表过长")
    private String blackList;

    public void setBlackList(String blackList) {
        this.blackList = StringUtils.replaceBlank(blackList);
    }
}
