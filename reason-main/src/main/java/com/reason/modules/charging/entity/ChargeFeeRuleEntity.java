package com.reason.modules.charging.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 充电费率（电费+服务费两段）
 *
 * @date 2026-09-05
 */
@Schema(description = "充电费率（电费+服务费两段）")
@Data
@TableName("charge_fee_rule")
public class ChargeFeeRuleEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 规则id
     */
    @Schema(description = "规则id")
    @TableId
    private Long ruleId;
    /**
     * 规则名称
     */
    @Schema(description = "规则名称")
    private String ruleName;
    /**
     * 电费单价（分/千瓦时）
     */
    @Schema(description = "电费单价（分/千瓦时）")
    private Integer elecPriceFen;
    /**
     * 服务费单价（分/千瓦时）
     */
    @Schema(description = "服务费单价（分/千瓦时）")
    private Integer servicePriceFen;
    /**
     * 状态：0-停用 1-启用
     */
    @Schema(description = "状态：0-停用 1-启用")
    private Integer ruleState;
    /**
     * 备注
     */
    @Schema(description = "备注")
    private String ruleRemark;
    /**
     * 创建时间（秒）
     */
    @Schema(description = "创建时间（秒）")
    private Long ruleCreatetime;
}
