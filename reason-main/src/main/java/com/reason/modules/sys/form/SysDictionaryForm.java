package com.reason.modules.sys.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 
 * 查询数据对象
 * @author author
 * @date 2023-03-18 15:05:04
 */
@Schema(description = "字典查询对象")
@Data
public class SysDictionaryForm extends CommonForm {
    @Schema(description = "分类  如 IP黑白名单 iplist 等-精确")
    private String dicSort;

    @Schema(description = "值")
    private String dicValue;
}
