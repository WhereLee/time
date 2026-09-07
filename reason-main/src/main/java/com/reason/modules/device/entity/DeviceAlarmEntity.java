package com.reason.modules.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备告警（device_alarm——异常显式化留痕）
 *
 * <p>"故障态+告警"机制：失败不悄悄重试、异常不静默吞掉——重试超限/离线/对账不一致/
 * 设备故障都落一条告警交给人。告警只"喊"，不改台账状态（状态只信设备事件）。</p>
 */
@Schema(description = "设备告警")
@Data
@TableName("device_alarm")
public class DeviceAlarmEntity {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long alarmId;

    @Schema(description = "设备编号")
    private String deviceNo;

    @Schema(description = "类型：1-指令重试超限 2-设备离线 3-状态对账不一致 4-设备故障上报")
    private Integer alarmType;

    @Schema(description = "告警内容（人可读）")
    private String alarmContent;

    @Schema(description = "处理状态：0-未处理 1-已处理")
    private Integer alarmHandled;

    @Schema(description = "告警时间戳(秒)")
    private Long alarmCreatetime;
}
