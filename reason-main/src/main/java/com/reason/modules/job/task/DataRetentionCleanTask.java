package com.reason.modules.job.task;

import com.reason.modules.job.dao.DataRetentionDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.LongToIntFunction;

/**
 * 表保留策略清理任务（批次5 D-G，job 203）
 *
 * <p>device_alarm / device_command_log / sys_log 保留 {@code reason.retention.days} 天（默认 90），
 * 超期行物理删除。防膨胀三原则：</p>
 * <ol>
 *   <li>分批 LIMIT：每批删除一段（默认 500 行）——独立短事务，不长事务锁表；</li>
 *   <li>单表批数上限：一轮最多删 max-batches 批（默认 200 × 500 = 10 万行/表）——
 *       意外巨量（如补数事故）不拖垮单轮；剩余部分次日继续（清理不追求单次清空）；</li>
 *   <li>超期判据=各表"创建时间"列（不可变列，无更新漂移）。</li>
 * </ol>
 *
 * <p>语义说明：不区分告警是否已处理——超期未处理告警一并清理（"问题跟进"靠告警运维流程，
 * 不是保留策略的职责）；被删除告警的审计价值在 90 天窗口内已覆盖。</p>
 *
 * <p><b>双时域 cutoff（批次5 D-H 附随修正）</b>：device_alarm / device_command_log 时间列已毫秒化
 * （09 迁移），sys_log 保持秒级——两类 cutoff 分别计算后传给对应 DAO，不可共用。</p>
 */
@Slf4j
@Component("dataRetentionCleanTask")
public class DataRetentionCleanTask implements ITask {

    /** 保留天数 */
    @Value("${reason.retention.days:90}")
    private int retentionDays;

    /** 每批删除行数（短事务粒度） */
    @Value("${reason.retention.batch-size:500}")
    private int batchSize;

    /** 单表单次批数上限 */
    @Value("${reason.retention.max-batches:200}")
    private int maxBatches;

    private final DataRetentionDao retentionDao;

    public DataRetentionCleanTask(DataRetentionDao retentionDao) {
        this.retentionDao = retentionDao;
    }

    @Override
    public void run(String params) {
        // D-H 双时域：设备两表毫秒、sys_log 秒（毫秒 cutoff 去比秒值会恒不命中；反之亦然）
        long cutoffMillis = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000;
        long cutoffSeconds = System.currentTimeMillis() / 1000 - retentionDays * 24L * 60 * 60;
        log.info("[表保留清理] 开始 cutoffMillis={} cutoffSeconds={}（{} 天前）batchSize={} maxBatches={}",
                cutoffMillis, cutoffSeconds, retentionDays, batchSize, maxBatches);
        int alarms = cleanInBatches("device_alarm", c -> retentionDao.deleteOldAlarms(c, batchSize), cutoffMillis);
        int commandLogs = cleanInBatches("device_command_log", c -> retentionDao.deleteOldCommandLogs(c, batchSize), cutoffMillis);
        int sysLogs = cleanInBatches("sys_log", c -> retentionDao.deleteOldSysLogs(c, batchSize), cutoffSeconds);
        log.info("[表保留清理] 完成 device_alarm={} device_command_log={} sys_log={}（超上限部分次日续清）",
                alarms, commandLogs, sysLogs);
    }

    /**
     * 分批删除直到取尽或达批数上限；返回删除总行数
     */
    private int cleanInBatches(String table, LongToIntFunction deleteBatch, long cutoff) {
        int total = 0;
        for (int i = 0; i < maxBatches; i++) {
            int deleted = deleteBatch.applyAsInt(cutoff);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        if (total > 0) {
            log.info("[表保留清理] {} 删除 {} 行", table, total);
        }
        return total;
    }
}
