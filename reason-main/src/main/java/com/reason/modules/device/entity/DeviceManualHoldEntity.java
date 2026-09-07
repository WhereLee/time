package com.reason.modules.device.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手动保持期落库（device_manual_hold——0.8：Redis 易失态的 DB 双写重建源）
 *
 * <p>保持期是"人在接管设备"的临时事实：Redis key（TTL 自动到期）+ DB 双写。
 * Redis 丢失（FLUSHDB/重启）后由 DB 重建——保证"人在杆下检修时自动规则不抢控"的
 * 物理安全语义不依赖 Redis 可用性（T14 修复）。</p>
 */
@Schema(description = "手动保持期(落库)")
@Data
@TableName("device_manual_hold")
public class DeviceManualHoldEntity {

    /** 设备编号（PK：一条设备最多一条活动保持期，新保持期覆盖旧的） */
    @TableId
    private String deviceNo;

    @Schema(description = "操作人(sys_user.user_id)")
    private Long userId;

    @Schema(description = "保持期到期时间戳(秒)")
    private Long expireTime;

    @Schema(description = "创建时间戳(秒)")
    private Long createTime;
}
