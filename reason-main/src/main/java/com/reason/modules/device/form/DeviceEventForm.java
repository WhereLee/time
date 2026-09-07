package com.reason.modules.device.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备事件上报表单（设备侧 → 平台：状态变化通知，协议 v2，见 contracts/PROTOCOL-V2.md）
 *
 * <p>v1 → v2 字段变更（0.1）：
 * commandSeq（可空）= 引起本次状态变化的指令序号——有值=指令驱动（平台按 seq 精确销账），
 * 空=外力改态/非指令驱动（平台只更新台账不动流水，不再"按动作猜"）；
 * (bootId, eventSeq) 必填 = 设备重启代际 + 设备内单调事件序号——台账序守卫据此拒绝
 * 重放/乱序/陈旧覆盖。时间戳仍由平台盖（单一时钟源：设备时钟不可信）。</p>
 */
@Schema(description = "设备事件上报表单(协议v2)")
@Data
public class DeviceEventForm {

    @Schema(description = "设备编号")
    private String deviceNo;

    @Schema(description = "新状态码：1-升起 2-降下 3-动作中 4-故障")
    private Integer state;

    @Schema(description = "引起本次状态变化的指令序号（可空：空=外力改态/非指令驱动）")
    private Long commandSeq;

    @Schema(description = "设备重启代际（进程启动UUID，必填）：重启后事件序号归零靠它区分代际")
    private String bootId;

    @Schema(description = "设备内单调递增事件序号（必填，重启后从1重计）")
    private Long eventSeq;
}
