package com.reason.modules.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.device.entity.DeviceOnlineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 设备在线台账 DAO（闸机/位检/充电桩统一在线语义）
 *
 * @date 2026-09-06
 */
@Mapper
public interface DeviceOnlineDao extends BaseMapper<DeviceOnlineEntity> {

    /**
     * 心跳批量上线：一批设备号一次 UPDATE（339 台/10s 场景下避免逐台 SQL）
     *
     * @param reportedAt 心跳时刻（秒）
     * @param deviceNos  本批上报在线设备号
     * @return 匹配行数（MySQL 返回 matched rows，非 changed rows）
     */
    @Update("<script>"
            + "UPDATE device_online SET device_state = 1, last_heartbeat = #{reportedAt}, "
            + "device_updatetime = #{reportedAt} "
            + "WHERE device_no IN "
            + "<foreach collection='deviceNos' item='no' open='(' separator=',' close=')'>#{no}</foreach>"
            + "</script>")
    int updateOnlineByNos(@Param("reportedAt") long reportedAt, @Param("deviceNos") List<String> deviceNos);

    /**
     * 设备明确上报离线（故障注入/网关检测到不可达）：立即置离线，心跳时刻不动
     *
     * @param deviceNos 本批上报离线设备号
     * @return 匹配行数
     */
    @Update("<script>"
            + "UPDATE device_online SET device_state = 0, "
            + "device_updatetime = unix_timestamp(now()) "
            + "WHERE device_no IN "
            + "<foreach collection='deviceNos' item='no' open='(' separator=',' close=')'>#{no}</foreach>"
            + "</script>")
    int updateOfflineByNos(@Param("deviceNos") List<String> deviceNos);

    /**
     * 设备巡检置离线：心跳超时（最后心跳早于阈值）的在线设备批量置离线
     *
     * @param thresholdSeconds 心跳超时阈值（秒，now - last_heartbeat &gt; 阈值即离线）
     * @return 本次置离线台数
     */
    @Update("UPDATE device_online SET device_state = 0, device_updatetime = unix_timestamp(now()) "
            + "WHERE device_state = 1 AND last_heartbeat < unix_timestamp(now()) - #{thresholdSeconds}")
    int offlineByHeartbeatTimeout(@Param("thresholdSeconds") long thresholdSeconds);
}
