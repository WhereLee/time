package com.reason.modules.device.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 闸机人工操作留痕（手动抬杆等异常放行审计）
 *
 * <p>防逃费关键审计表：操作人/原因必录，车牌人工录入；
 * 指令结果无论成败都落痕（设备不可达也留现场），杜绝"人工放行=系统消失"。 </p>
 *
 * @date 2026-09-06
 */
@Schema(description = "闸机人工操作留痕（手动抬杆审计）")
@Data
@TableName("gate_manual_op")
public class GateManualOpEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 操作id
     */
    @Schema(description = "操作id")
    @TableId
    private Long opId;
    /**
     * 目标闸机设备号（如 GATE-E-OUT）
     */
    @Schema(description = "目标闸机设备号")
    private String deviceNo;
    /**
     * 出入口编码（E/S/W）
     */
    @Schema(description = "出入口编码（E/S/W）")
    private String gateCode;
    /**
     * 操作类型：1-手动抬杆
     */
    @Schema(description = "操作类型：1-手动抬杆")
    private Integer opType;
    /**
     * 车牌号（人工录入；空=未识别/无牌）
     */
    @Schema(description = "车牌号（人工录入）")
    private String plateNo;
    /**
     * 操作原因（设备故障/特殊放行/收费争议）
     */
    @Schema(description = "操作原因")
    private String opReason;
    /**
     * 指令结果：0-成功 1-设备不可达
     */
    @Schema(description = "指令结果：0-成功 1-设备不可达")
    private Integer opResult;
    /**
     * 结果说明（失败原因等）
     */
    @Schema(description = "结果说明")
    private String opRemark;
    /**
     * 操作人（sys_user.user_id）
     */
    @Schema(description = "操作人（sys_user.user_id）")
    private Long operatorId;
    /**
     * 操作人账号冗余
     */
    @Schema(description = "操作人账号冗余")
    private String operatorName;
    /**
     * 操作时间（秒）
     */
    @Schema(description = "操作时间（秒）")
    private Long opCreatetime;
}
