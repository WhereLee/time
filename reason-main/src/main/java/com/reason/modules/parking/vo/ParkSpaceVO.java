package com.reason.modules.parking.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 车位新增/修改入参（写操作 VO；查询直出实体）
 *
 * @date 2026-09-05
 */
@Schema(description = "车位新增/修改参数")
@Data
public class ParkSpaceVO {

    /**
     * 车位id（修改必传，新增忽略）
     */
    @Schema(description = "车位id（修改必传）")
    private Long spaceId;
    /**
     * 车位编号（如 A-001，全局唯一；应用层统一大写入库）
     */
    @Schema(description = "车位编号（如 A-001，全局唯一）")
    private String spaceNo;
    /**
     * 区域/位置描述（如：A区-东侧）
     */
    @Schema(description = "区域/位置描述")
    private String spaceArea;
    /**
     * 状态：0-空闲 1-占用 2-禁用
     * 新增默认空闲；新增/修改均不允许置为占用（占用仅由入场事务产生）
     */
    @Schema(description = "状态：0-空闲 2-禁用（占用仅由入场事务产生，不可手动设置）")
    private Integer spaceState;
}
