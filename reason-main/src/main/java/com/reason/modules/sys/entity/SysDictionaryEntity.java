package com.reason.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.reason.modules.sys.vo.SysDictionaryVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import lombok.Data;

/**
 * 
 * 
 * @author author
 * @date 2023-03-09 15:18:02
 */
@Schema(description = "字典实体")
@Data
@TableName("sys_dictionary")
public class SysDictionaryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 主键 自增
	 */
    @Schema(description = "主键 自增")
	@TableId
	private Long dicId;
	/**
	 * 分类  如 IP黑白名单 iplist 等
	 */
    @Schema(description = "分类  如 IP黑白名单 iplist 等")
	private String dicSort;
	/**
	 * Key
	 */
    @Schema(description = "Key")
	private String dicKey;
	/**
	 * 值
	 */
    @Schema(description = "值")
	private String dicValue;
	/**
	 * 说明、备注
	 */
    @Schema(description = "说明、备注")
	private String dicRemark;
	/**
	 * 创建人
	 */
	@Schema(description = "创建人ID")
	private Long dicCreator;
	/**
	 * 创建时间戳，单位秒
	 */
	@Schema(description = "创建时间戳（秒）")
	private Long dicCreatetime;
	/**
	 * 更新时间戳，单位秒
	 */
	@Schema(description = "更新时间戳（秒）")
	private Long dicUpdatetime;
	/**
	 * 状态 0-有效 >0 无效 默认0
	 */
	@Schema(description = "状态标志 状态 0-有效 >0 无效 默认0")
	private Long dicStatus;

    public SysDictionaryEntity() {}

	/**
	 * 新增或修改
	 * @param vo
	 * @param type 1-新增 2-修改
	 * @param key
	 */
	public SysDictionaryEntity(SysDictionaryVO vo, Integer type, Integer key, Long creator) {
		Long timestamp = System.currentTimeMillis()/1000;
		this.dicValue = vo.getDicValue();
		this.dicRemark = vo.getDicRemark();
		this.dicUpdatetime = timestamp;
		if (type == 1) {
			this.dicSort = vo.getDicSort();
			this.dicKey = key + "";
			this.dicCreator = creator;
			this.dicCreatetime = timestamp;
		} else
			this.dicId = vo.getDicId();
	}

	/**
	 * 删除-将status=id
	 * @param dicId
	 */
	public SysDictionaryEntity(Long dicId) {
		this.dicId = dicId;
		this.dicStatus = dicId;
		this.dicUpdatetime = System.currentTimeMillis()/1000;
	}

	/**
	 * 判断数据是否有效，即未删除
	 * @return true：是
	 */
	public boolean valid() {
		return (dicStatus != null && dicStatus == 0);
	}
}
