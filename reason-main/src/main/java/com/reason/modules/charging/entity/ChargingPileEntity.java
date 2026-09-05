package com.reason.modules.charging.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 充电桩台账（绑车位 1:1，空间主数据共享停车域）
 *
 * @date 2026-09-05
 */
@Schema(description = "充电桩台账（绑车位 1:1，空间主数据共享停车域）")
@Data
@TableName("charging_pile")
public class ChargingPileEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 桩id
     */
    @Schema(description = "桩id")
    @TableId
    private Long pileId;
    /**
     * 桩编号（唯一）
     */
    @Schema(description = "桩编号（唯一）")
    private String pileNo;
    /**
     * 绑定车位id（park_space.space_id）
     */
    @Schema(description = "绑定车位id（park_space.space_id）")
    private Long spaceId;
    /**
     * 状态：0-空闲 1-充电中 2-停用
     */
    @Schema(description = "状态：0-空闲 1-充电中 2-停用")
    private Integer pileState;
    /**
     * 创建人（sys_user.user_id）
     */
    @Schema(description = "创建人（sys_user.user_id）")
    private Long pileCreator;
    /**
     * 创建时间（秒）
     */
    @Schema(description = "创建时间（秒）")
    private Long pileCreatetime;
    /**
     * 更新时间（秒）
     */
    @Schema(description = "更新时间（秒）")
    private Long pileUpdatetime;
}
