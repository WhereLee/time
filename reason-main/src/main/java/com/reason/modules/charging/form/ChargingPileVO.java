package com.reason.modules.charging.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 充电桩参数（新增/修改共用；修改需带 pileId）
 *
 * @date 2026-09-05
 */
@Schema(description = "充电桩参数")
@Data
public class ChargingPileVO {

    @Schema(description = "桩id（修改必传）")
    private Long pileId;
    @Schema(description = "桩编号（大写归一）")
    private String pileNo;
    @Schema(description = "绑定车位 id（须存在/未停用/无进行中停车）")
    private Long spaceId;
    @Schema(description = "状态：0-空闲 2-停用（1-充电中由会话事务管理，不接受手动置位）")
    private Integer pileState;
}
