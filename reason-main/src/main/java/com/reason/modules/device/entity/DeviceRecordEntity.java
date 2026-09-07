package com.reason.modules.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 设备台账实体（"杆的档案"——对象设计中的台账对象）
 *
 * <p>只管"这台设备是谁、装在哪、最近一次知道的状态"，不执行任何设备动作；
 * 状态字段是快照，只允许设备上报事件驱动更新（反馈闭环），禁止指令侧自改。</p>
 */
@Schema(description = "设备台账")
@Data
@TableName("device_record")
public class DeviceRecordEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long deviceId;

    @Schema(description = "设备编号（唯一，指令寻址/事件上报按此对齐）")
    private String deviceNo;

    @Schema(description = "设备名称（如：东门一号杆）")
    private String deviceName;

    @Schema(description = "设备类型：1-道闸/升降杆")
    private Integer deviceType;

    @Schema(description = "安装位置")
    private String location;

    @Schema(description = "状态快照：0-未接入 1-升起 2-降下 3-动作中 4-故障")
    private Integer deviceState;

    @Schema(description = "备注")
    private String deviceRemark;

    @Schema(description = "登记人 sys_user.user_id")
    private Long deviceCreator;

    @Schema(description = "登记时间戳(秒)")
    private Long deviceCreatetime;

    @Schema(description = "更新时间戳(秒)")
    private Long deviceUpdatetime;

    /**
     * 在线状态（展示字段，不落库）：在线是"此刻"的事实（Redis 心跳 key TTL 判定），
     * 落库即过时——查询时实时填充，台账表里没有这一列
     */
    @Schema(description = "是否在线（心跳 TTL 实时判定，不落库）")
    @TableField(exist = false)
    private Boolean online;
}
