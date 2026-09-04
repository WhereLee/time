package com.reason.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 停车会话（状态机：0进行中 → 1已结束 | 2已取消，终态不可逆）
 *
 * @date 2026-09-04
 */
@Schema(description = "停车会话")
@Data
@TableName("park_session")
public class ParkSessionEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 会话id
     */
    @Schema(description = "会话id")
    @TableId
    private Long sessionId;
    /**
     * 车位id（park_space.space_id）
     */
    @Schema(description = "车位id")
    private Long spaceId;
    /**
     * 车位编号冗余（会话期间车位编号，列表展示免 join）
     */
    @Schema(description = "车位编号")
    private String spaceNo;
    /**
     * 车牌号（应用层统一大写，兼容新能源8位）
     */
    @Schema(description = "车牌号")
    private String plateNo;
    /**
     * 入场时间戳，单位秒
     */
    @Schema(description = "入场时间戳（秒）")
    private Long sessionEntryTime;
    /**
     * 出场时间戳，单位秒（进行中为空）
     */
    @Schema(description = "出场时间戳（秒）")
    private Long sessionExitTime;
    /**
     * 取消时间戳，单位秒（未取消为空）
     */
    @Schema(description = "取消时间戳（秒）")
    private Long sessionCancelTime;
    /**
     * 状态：0-进行中 1-已结束 2-已取消
     */
    @Schema(description = "状态：0-进行中 1-已结束 2-已取消")
    private Integer sessionState;
    /**
     * 取消原因（操作端可选填）
     */
    @Schema(description = "取消原因")
    private String sessionCancelReason;
    /**
     * 最近状态变更时间戳，单位秒
     */
    @Schema(description = "更新时间戳（秒）")
    private Long sessionUpdatetime;
}
