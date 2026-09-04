package com.reason.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 停车位台账（设备控制型智慧停车位；无物理删除，删除=禁用）
 *
 * @date 2026-09-04
 */
@Schema(description = "停车位")
@Data
@TableName("park_space")
public class ParkSpaceEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 车位id
     */
    @Schema(description = "车位id")
    @TableId
    private Long spaceId;
    /**
     * 车位编号（如 A-001，全局唯一）
     */
    @Schema(description = "车位编号")
    private String spaceNo;
    /**
     * 区域/位置描述（如：A区-东侧）
     */
    @Schema(description = "区域/位置描述")
    private String spaceArea;
    /**
     * 状态：0-空闲 1-占用 2-禁用
     */
    @Schema(description = "状态：0-空闲 1-占用 2-禁用")
    private Integer spaceState;
    /**
     * 建档人（sys_user.user_id）
     */
    @Schema(description = "建档人")
    private Long spaceCreator;
    /**
     * 建档时间戳，单位秒
     */
    @Schema(description = "建档时间戳（秒）")
    private Long spaceCreatetime;
    /**
     * 最近更新时间戳，单位秒
     */
    @Schema(description = "更新时间戳（秒）")
    private Long spaceUpdatetime;
}
