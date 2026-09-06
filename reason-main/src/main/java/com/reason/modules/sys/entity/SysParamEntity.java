package com.reason.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reason.modules.sys.vo.SysParamVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 
 * 
 * @date 2020-04-29 10:10:57
 */
@Schema(description = "参数对象")
@Data
@TableName("sys_param")
public class SysParamEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 主键ID
	 */
	@Schema(description = "参数ID")
	@TableId
	private Long paramId;
	/**
	 * 参数的名称，不可修改，显示到页面
	 */
	@Schema(description = "参数名称")
	private String paramName;
	/**
	 * 参数的Key，不可修改，后台调用的时候用
	 */
	@Schema(description = "参数Key")
	private String paramKey;
	/**
	 * 参数的值
	 */
	@Schema(description = "参数值")
	private String paramValue;
	/**
	 * 说明、备注
	 */
	@Schema(description = "参数说明/备注")
	private String paramComment;
	/**
	 * 创建时间戳，单位秒
	 */
	@Schema(description = "创建时间戳（秒）")
	private Long paramCreatetime;
	/**
	 * 修改时间戳，单位秒
	 */
	@Schema(description = "修改时间戳（秒）")
	private Long paramUpdatetime;
	/**
	 * 参数开放标志 0-开放，1-关闭
	 */
	@Schema(description = "回收标志 0-正常 1-回收")
	private Integer paramRecycle;

	public SysParamEntity() {}

	/**
	 * 关闭或开放
	 * @param paramId
	 * @param paramRecycle
	 */
	public SysParamEntity(Long paramId, Integer paramRecycle) {
		this.paramId = paramId;
		this.paramRecycle = paramRecycle;
		this.paramUpdatetime = System.currentTimeMillis()/1000;
	}

	/**
	 * 新增或修改
	 * @param paramVO 前端传入参数对象
	 * @param type 1-新增 2-修改
	 */
	public SysParamEntity(SysParamVO paramVO, Integer type) {
		this.paramName = paramVO.getParamName();
		this.paramKey = paramVO.getParamKey();
		this.paramValue = paramVO.getParamValue();
		this.paramComment = paramVO.getParamComment();
		if (type == 1) {
			this.paramCreatetime = System.currentTimeMillis()/1000;
			this.paramUpdatetime = System.currentTimeMillis()/1000;
		} else if (type == 2) {
			this.paramId = paramVO.getParamId();
			this.paramUpdatetime = System.currentTimeMillis()/1000;
		}
	}

	/**
	 * 判断是否开放
	 * @return true：开放
	 */
	public boolean open () {
		return (paramRecycle != null && paramRecycle == 0);
	}

}
