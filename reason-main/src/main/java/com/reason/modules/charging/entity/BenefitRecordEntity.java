package com.reason.modules.charging.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 免停权益（跨方凭证：charging 签发，parking 凭码核销）
 *
 * @date 2026-09-05
 */
@Schema(description = "免停权益（跨方凭证：charging 签发，parking 凭码核销）")
@Data
@TableName("benefit_record")
public class BenefitRecordEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 权益id
     */
    @Schema(description = "权益id")
    @TableId
    private Long benefitId;
    /**
     * 权益码（凭证：BN+时间戳+随机）
     */
    @Schema(description = "权益码（凭证：BN+时间戳+随机）")
    private String benefitNo;
    /**
     * 来源充电订单id
     */
    @Schema(description = "来源充电订单id")
    private Long sourceOrderId;
    /**
     * 车牌号
     */
    @Schema(description = "车牌号")
    private String plateNo;
    /**
     * 锚定停车会话id（核销须同会话）
     */
    @Schema(description = "锚定停车会话id（核销须同会话）")
    private Long anchorSessionId;
    /**
     * 免停时长（秒）
     */
    @Schema(description = "免停时长（秒）")
    private Integer freeSeconds;
    /**
     * 到期时间（秒）
     */
    @Schema(description = "到期时间（秒）")
    private Long expireTime;
    /**
     * 状态：0-可用 1-已核销 2-已过期
     */
    @Schema(description = "状态：0-可用 1-已核销 2-已过期")
    private Integer benefitState;
    /**
     * 核销停车会话id
     */
    @Schema(description = "核销停车会话id")
    private Long redeemSessionId;
    /**
     * 核销停车订单id
     */
    @Schema(description = "核销停车订单id")
    private Long redeemOrderId;
    /**
     * 签发时间（秒）
     */
    @Schema(description = "签发时间（秒）")
    private Long benefitCreatetime;
    /**
     * 核销时间（秒）
     */
    @Schema(description = "核销时间（秒）")
    private Long redeemTime;
}
