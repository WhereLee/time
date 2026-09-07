package com.reason.modules.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备指令流水（device_command_log——指令闭环的账本）
 *
 * <p>每次下发（手动/自动/重试）记一条：seq 是幂等键（设备内单调递增，重发同 seq
 * 设备侧去重），status 是平台视角的指令生命周期（待到位→已到位/失败/超限）。
 * 账本 ≠ 台账：账本记"平台发过什么、等到没等到"，台账记"设备最近说自己是什么"。</p>
 */
@Schema(description = "设备指令流水")
@Data
@TableName("device_command_log")
public class DeviceCommandLogEntity {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long commandId;

    @Schema(description = "设备编号")
    private String deviceNo;

    @Schema(description = "指令动作：OPEN-升起 CLOSE-降下")
    private String commandAction;

    @Schema(description = "指令序号（设备内单调递增，幂等去重键）")
    private Long commandSeq;

    @Schema(description = "触发源：1-管理端手动 2-自动规则 3-超时重试")
    private Integer triggerType;

    @Schema(description = "状态：0-已下发待到位 1-已到位 2-下发失败 3-重试超限转故障 4-执行失败(设备故障)")
    private Integer commandStatus;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "下发时间戳(秒)")
    private Long commandCreatetime;

    @Schema(description = "更新时间戳(秒)")
    private Long commandUpdatetime;
}
