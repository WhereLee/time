package com.reason.modules.job.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 表保留策略清理 DAO（批次5 D-G）
 *
 * <p>三张易膨胀表的物理清理（保留期见 DataRetentionCleanTask 配置）。
 * 分批 LIMIT 由调用方控制：每批一句短 DELETE（独立事务），防超期数据量大时单条
 * 长事务锁表。LIMIT 采用参数化（MySQL 支持 prepared LIMIT）。</p>
 */
public interface DataRetentionDao {

    /** 清理超期告警（按告警时间） */
    @Delete("DELETE FROM device_alarm WHERE alarm_createtime < #{cutoff} LIMIT #{limit}")
    int deleteOldAlarms(@Param("cutoff") long cutoff, @Param("limit") int limit);

    /** 清理超期指令流水（按下发时间） */
    @Delete("DELETE FROM device_command_log WHERE command_createtime < #{cutoff} LIMIT #{limit}")
    int deleteOldCommandLogs(@Param("cutoff") long cutoff, @Param("limit") int limit);

    /** 清理超期操作日志（按创建时间；历史 NULL 行不满足比较条件，天然豁免） */
    @Delete("DELETE FROM sys_log WHERE log_createtime < #{cutoff} LIMIT #{limit}")
    int deleteOldSysLogs(@Param("cutoff") long cutoff, @Param("limit") int limit);
}
