package com.reason.modules.device.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 设备在线台账（闸机/位检/充电桩统一在线语义）
 *
 * <p>本表只持绑定标识（出入口编码/车位号/桩号），不直连业务表；
 * 业务台账（park_space/charging_pile）在各业务域，设备域与业务域通过编号衔接。</p>
 *
 * @date 2026-09-06
 */
@Schema(description = "设备在线台账（闸机/位检/充电桩统一在线语义）")
@Data
@TableName("device_online")
public class DeviceOnlineEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 设备id
     */
    @Schema(description = "设备id")
    @TableId
    private Long deviceId;
    /**
     * 设备编号（全局唯一；充电桩与桩编号一致，sim 心跳/指令寻址）
     */
    @Schema(description = "设备编号（全局唯一；充电桩与桩编号一致）")
    private String deviceNo;
    /**
     * 类型：0-入口闸机 1-出口闸机 2-位检 3-充电桩
     */
    @Schema(description = "类型：0-入口闸机 1-出口闸机 2-位检 3-充电桩")
    private Integer deviceType;
    /**
     * 绑定对象：出入口编码(E/S/W)/车位号/桩号（纯标识，不直连业务表）
     */
    @Schema(description = "绑定对象：出入口编码(E/S/W)/车位号/桩号")
    private String bindTarget;
    /**
     * 在线态：0-离线 1-在线（与业务态分离，仅表示可通信）
     */
    @Schema(description = "在线态：0-离线 1-在线")
    private Integer deviceState;
    /**
     * 最后心跳时间（秒）
     */
    @Schema(description = "最后心跳时间（秒）")
    private Long lastHeartbeat;
    /**
     * 备注（位置/车道说明）
     */
    @Schema(description = "备注（位置/车道说明）")
    private String deviceRemark;
    /**
     * 创建人（sys_user.user_id）
     */
    @Schema(description = "创建人（sys_user.user_id）")
    private Long deviceCreator;
    /**
     * 创建时间（秒）
     */
    @Schema(description = "创建时间（秒）")
    private Long deviceCreatetime;
    /**
     * 更新时间（秒）
     */
    @Schema(description = "更新时间（秒）")
    private Long deviceUpdatetime;
}
