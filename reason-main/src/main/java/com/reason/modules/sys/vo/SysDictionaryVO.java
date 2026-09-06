package com.reason.modules.sys.vo;


import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * 
 * 前端传入参数对象封装
 * @author author
 * @date 2023-03-18 15:05:04
 */
@Schema(description = "字典VO")
@Data
public class SysDictionaryVO implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 主键 自增
	 */
    @Schema(description = "字典主键")
	@NotNull(message = "字典主键不能为空",groups = {UpdateGroup.class})
	private Long dicId;
	/**
	 * 分类  如 IP黑白名单 iplist 等
	 */
    @Schema(description = "分类  如 IP黑白名单 iplist 等")
	@Size(max = 64,message = "分类过长")
	@NotBlank(message = "分类不能为空",groups = {AddGroup.class})
	private String dicSort;
	/**
	 * 值
	 */
    @Schema(description = "值")
	@Size(max = 2048,message = "值过长")
	@NotBlank(message = "值不能为空",groups = {AddGroup.class})
	private String dicValue;
	/**
	 * 说明、备注
	 */
    @Schema(description = "说明、备注")
	@Size(max = 256,message = "备注过长")
	private String dicRemark;

}
