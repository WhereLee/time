package com.reason.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 计费规则（M0 单规则：按小时向上取整；多策略/峰谷阶梯属 M2 规则引擎）
 *
 * @date 2026-09-04
 */
@Schema(description = "计费规则")
@Data
@TableName("fee_rule")
public class FeeRuleEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 规则id
     */
    @Schema(description = "规则id")
    @TableId
    private Long ruleId;
    /**
     * 规则名称（如：标准计时收费）
     */
    @Schema(description = "规则名称")
    private String ruleName;
    /**
     * 单价，单位：分/小时（M0 按小时向上取整计费）
     */
    @Schema(description = "单价（分/小时）")
    private Integer unitPriceFen;
    /**
     * 状态：0-停用 1-启用（M0 仅一条启用规则）
     */
    @Schema(description = "状态：0-停用 1-启用")
    private Integer ruleState;
    /**
     * 说明备注
     */
    @Schema(description = "说明备注")
    private String ruleRemark;
    /**
     * 创建人（sys_user.user_id，预置数据为空）
     */
    @Schema(description = "创建人")
    private Long ruleCreator;
    /**
     * 创建时间戳，单位秒
     */
    @Schema(description = "创建时间戳（秒）")
    private Long ruleCreatetime;
    /**
     * 最近更新时间戳，单位秒
     */
    @Schema(description = "更新时间戳（秒）")
    private Long ruleUpdatetime;
}
