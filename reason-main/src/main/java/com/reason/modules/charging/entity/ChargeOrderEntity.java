package com.reason.modules.charging.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 充电订单（电量两段计费快照，生成后不可变）
 *
 * @date 2026-09-05
 */
@Schema(description = "充电订单（电量两段计费快照，生成后不可变）")
@Data
@TableName("charge_order")
public class ChargeOrderEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订单id
     */
    @Schema(description = "订单id")
    @TableId
    private Long orderId;
    /**
     * 会话id（唯一）
     */
    @Schema(description = "会话id（唯一）")
    private Long sessionId;
    /**
     * 桩编号快照
     */
    @Schema(description = "桩编号快照")
    private String pileNo;
    /**
     * 车位编号快照
     */
    @Schema(description = "车位编号快照")
    private String spaceNo;
    /**
     * 车牌号快照
     */
    @Schema(description = "车牌号快照")
    private String plateNo;
    /**
     * 开始充电时间快照（秒）
     */
    @Schema(description = "开始充电时间快照（秒）")
    private Long orderStartTime;
    /**
     * 结束时间快照（秒）
     */
    @Schema(description = "结束时间快照（秒）")
    private Long orderEndTime;
    /**
     * 电量快照（瓦时）
     */
    @Schema(description = "电量快照（瓦时）")
    private Long energyWh;
    /**
     * 电费单价快照（分/千瓦时）
     */
    @Schema(description = "电费单价快照（分/千瓦时）")
    private Integer elecPriceFen;
    /**
     * 服务费单价快照（分/千瓦时）
     */
    @Schema(description = "服务费单价快照（分/千瓦时）")
    private Integer servicePriceFen;
    /**
     * 电费金额（分，各自四舍五入）
     */
    @Schema(description = "电费金额（分，各自四舍五入）")
    private Long elecAmountFen;
    /**
     * 服务费金额（分，各自四舍五入）
     */
    @Schema(description = "服务费金额（分，各自四舍五入）")
    private Long serviceAmountFen;
    /**
     * 总金额（分 = 电费+服务费，快照恒等）
     */
    @Schema(description = "总金额（分 = 电费+服务费，快照恒等）")
    private Long amountFen;
    /**
     * 状态：0-已生成（M1 终态）
     */
    @Schema(description = "状态：0-已生成（M1 终态）")
    private Integer orderState;
    /**
     * 创建时间（秒）
     */
    @Schema(description = "创建时间（秒）")
    private Long orderCreatetime;
}
