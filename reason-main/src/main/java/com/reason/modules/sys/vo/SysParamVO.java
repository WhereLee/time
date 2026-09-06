package com.reason.modules.sys.vo;

import com.reason.common.utils.StringUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "参数VO")
@Data
public class SysParamVO {
    @Schema(description = "参数ID")
    @NotNull(message = "参数主键不能为空",groups = {UpdateGroup.class})
    private Long paramId;

    @Schema(description = "参数名称")
    @Size(max = 256,message = "参数名称过长",groups = {AddGroup.class})
    @NotBlank(message="参数名称不能为空",groups = {AddGroup.class})
    private String paramName;

    public void setParamName(String paramName) {
        this.paramName = StringUtils.replaceBlank(paramName);
    }

    @Schema(description = "参数Key")
    @Size(max = 256,message = "参数Key过长",groups = {AddGroup.class})
    @NotBlank(message="参数Key不能为空",groups = {AddGroup.class})
    private String paramKey;

    public void setParamKey(String paramKey) {
        this.paramKey = StringUtils.replaceBlank(paramKey);
    }

    @Schema(description = "参数值")
    @Size(max = 256,message = "参数值过长",groups = {AddGroup.class,UpdateGroup.class})
    @NotNull(message = "参数值不能为空",groups = {AddGroup.class})
    private String paramValue;

    public void setParamValue(String paramValue) {
        this.paramValue = StringUtils.replaceBlank(paramValue);
    }

    @Schema(description = "参数说明")
    @Size(max = 256,message = "参数说明过长",groups = {AddGroup.class})
    @NotNull(message = "参数说明不能为空",groups = {AddGroup.class})
    private String paramComment;

    public void setParamComment(String paramComment) {
        this.paramComment = StringUtils.replaceBlank(paramComment);
    }

}
