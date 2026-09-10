package com.reason.modules.job.task;

import com.reason.modules.job.dao.DataRetentionDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表保留清理任务单测（批次5 D-G：分批短事务 + 批数封顶 + cutoff 口径）
 */
@DisplayName("表保留清理任务（批次5）")
@ExtendWith(MockitoExtension.class)
class DataRetentionCleanTaskTest {

    @Mock
    private DataRetentionDao retentionDao;

    private DataRetentionCleanTask newTask(int days, int batchSize, int maxBatches) {
        DataRetentionCleanTask task = new DataRetentionCleanTask(retentionDao);
        ReflectionTestUtils.setField(task, "retentionDays", days);
        ReflectionTestUtils.setField(task, "batchSize", batchSize);
        ReflectionTestUtils.setField(task, "maxBatches", maxBatches);
        return task;
    }

    @Test
    @DisplayName("分批删除：删除量小于批容量即停（取尽）")
    void 分批删除_取尽即停() {
        when(retentionDao.deleteOldAlarms(anyLong(), anyInt())).thenReturn(2, 2, 1);
        when(retentionDao.deleteOldCommandLogs(anyLong(), anyInt())).thenReturn(1);
        when(retentionDao.deleteOldSysLogs(anyLong(), anyInt())).thenReturn(0);

        newTask(90, 2, 5).run(null);

        verify(retentionDao, times(3)).deleteOldAlarms(anyLong(), anyInt());
        verify(retentionDao, times(1)).deleteOldCommandLogs(anyLong(), anyInt());
        verify(retentionDao, times(1)).deleteOldSysLogs(anyLong(), anyInt());
    }

    @Test
    @DisplayName("批数上限：恒满批时封顶（防意外巨量拖垮单轮，次日续清）")
    void 批数上限_封顶() {
        when(retentionDao.deleteOldAlarms(anyLong(), anyInt())).thenReturn(5);
        when(retentionDao.deleteOldCommandLogs(anyLong(), anyInt())).thenReturn(0);
        when(retentionDao.deleteOldSysLogs(anyLong(), anyInt())).thenReturn(0);

        newTask(90, 5, 3).run(null);

        verify(retentionDao, times(3)).deleteOldAlarms(anyLong(), anyInt());
    }

    @Test
    @DisplayName("cutoff 双时域：设备两表毫秒、sys_log 秒（D-H 后不可共用）")
    void cutoff_双时域() {
        ArgumentCaptor<Long> alarmCutoff = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> cmdCutoff = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> sysCutoff = ArgumentCaptor.forClass(Long.class);
        when(retentionDao.deleteOldAlarms(alarmCutoff.capture(), anyInt())).thenReturn(0);
        when(retentionDao.deleteOldCommandLogs(cmdCutoff.capture(), anyInt())).thenReturn(0);
        when(retentionDao.deleteOldSysLogs(sysCutoff.capture(), anyInt())).thenReturn(0);

        newTask(90, 500, 200).run(null);

        long expectedMs = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000;
        long expectedS = System.currentTimeMillis() / 1000 - 90L * 24 * 60 * 60;
        assertThat(alarmCutoff.getValue()).isBetween(expectedMs - 5000, expectedMs + 5000);
        assertThat(cmdCutoff.getValue()).isBetween(expectedMs - 5000, expectedMs + 5000);
        assertThat(sysCutoff.getValue()).isBetween(expectedS - 5, expectedS + 5);
    }
}
