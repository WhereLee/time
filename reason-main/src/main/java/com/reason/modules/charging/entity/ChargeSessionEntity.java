package com.reason.modules.charging.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 充电会话（锚定停车会话，充电必须发生在停车中）
 *
 * @date 2026-09-05
 */
@Schema(description = "充电会话（锚定停车会话，充电必须发生在停车中）")
@Data
@TableName("charge_session")
public class ChargeSessionEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 会话id
     */
    @Schema(description = "会话id")
    @TableId
    private Long sessionId;
    /**
     * 桩id
     */
    @Schema(description = "桩id")
    private Long pileId;
    /**
     * 桩编号快照
     */
    @Schema(description = "桩编号快照")
    private String pileNo;
    /**
     * 车位id
     */
    @Schema(description = "车位id")
    private Long spaceId;
    /**
     * 车位编号快照
     */
    @Schema(description = "车位编号快照")
    private String spaceNo;
    /**
     * 车牌号（与锚定停车会话车牌一致校验）
     */
    @Schema(description = "车牌号（与锚定停车会话车牌一致校验）")
    private String plateNo;
    /**
     * 锚定停车会话id（park_session.session_id）
     */
    @Schema(description = "锚定停车会话id（park_session.session_id）")
    private Long anchorSessionId;
    /**
     * 开始充电时间（秒）
     */
    @Schema(description = "开始充电时间（秒）")
    private Long sessionStartTime;
    /**
     * 结束时间（秒）
     */
    @Schema(description = "结束时间（秒）")
    private Long sessionEndTime;
    /**
     * 总电量（瓦时）
     */
    @Schema(description = "总电量（瓦时）")
    private Long energyWh;
    /**
     * 状态：0-充电中 1-已结束 2-已取消 3-超时结束
     */
    @Schema(description = "状态：0-充电中 1-已结束 2-已取消 3-超时结束")
    private Integer sessionState;
    /**
     * 取消/超时原因
     */
    @Schema(description = "取消/超时原因")
    private String cancelReason;
    /**
     * 创建时间（秒）
     */
    @Schema(description = "创建时间（秒）")
    private Long sessionCreatetime;
    /**
     * 更新时间（秒）
     */
    @Schema(description = "更新时间（秒）")
    private Long sessionUpdatetime;
}
