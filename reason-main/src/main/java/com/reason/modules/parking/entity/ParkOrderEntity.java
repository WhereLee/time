package com.reason.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 停车订单（结算时点快照，生成后不可变；金额统一分存储）
 *
 * @date 2026-09-04
 */
@Schema(description = "停车订单")
@Data
@TableName("park_order")
public class ParkOrderEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订单id
     */
    @Schema(description = "订单id")
    @TableId
    private Long orderId;
    /**
     * 会话id（park_session.session_id，唯一）
     */
    @Schema(description = "会话id")
    private Long sessionId;
    /**
     * 车牌号快照
     */
    @Schema(description = "车牌号")
    private String plateNo;
    /**
     * 车位编号快照（出场后车位可被复用）
     */
    @Schema(description = "车位编号")
    private String spaceNo;
    /**
     * 入场时间戳快照，单位秒
     */
    @Schema(description = "入场时间戳（秒）")
    private Long orderEntryTime;
    /**
     * 出场时间戳快照，单位秒
     */
    @Schema(description = "出场时间戳（秒）")
    private Long orderExitTime;
    /**
     * 停车时长（分钟）
     */
    @Schema(description = "停车时长（分钟）")
    private Integer durationMinutes;
    /**
     * 结算单价快照，单位：分/小时（规则后续调价不影响历史订单）
     */
    @Schema(description = "结算单价（分/小时）")
    private Integer unitPriceFen;
    /**
     * 应收金额（分）
     */
    @Schema(description = "应收金额（分）")
    private Long amountFen;
    /**
     * 减免金额（分，无减免为 0；amount_fen 保持减免前应收原义，实付=应收−减免可推导）
     */
    @Schema(description = "减免金额（分）")
    private Long discountFen;
    /**
     * 核销权益码快照（无减免为空；跨方凭证追溯）
     */
    @Schema(description = "核销权益码")
    private String benefitNo;
    /**
     * 状态：0-已生成（M0 终态；支付状态 M2 结算域扩展）
     */
    @Schema(description = "状态：0-已生成")
    private Integer orderState;
    /**
     * 订单生成时间戳，单位秒
     */
    @Schema(description = "生成时间戳（秒）")
    private Long orderCreatetime;
}
