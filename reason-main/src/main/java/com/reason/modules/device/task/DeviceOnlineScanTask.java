package com.reason.modules.device.task;

import com.reason.modules.device.service.DeviceOnlineService;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备在线巡检（Quartz 注册：schedule_job 表 job_bean=deviceOnlineScanTask，每 15s）
 *
 * <p>心跳超时判定兜底：网关心跳丢失/上报中断时，把心跳超时的在线设备批量置离线——
 * 在线台账的最终裁决者（心跳批只管置在线与显式离线，超时离线靠本任务收敛）。</p>
 *
 * @date 2026-09-06
 */
@Slf4j
@Component("deviceOnlineScanTask")
public class DeviceOnlineScanTask implements ITask {

    /** 心跳超时阈值（秒）：超过该窗口无心跳即判离线（与心跳周期 10s 匹配，容忍 2 个周期） */
    private static final long OFFLINE_THRESHOLD_SECONDS = 30;

    private final DeviceOnlineService deviceOnlineService;

    public DeviceOnlineScanTask(DeviceOnlineService deviceOnlineService) {
        this.deviceOnlineService = deviceOnlineService;
    }

    @Override
    public void run(String params) {
        int count = deviceOnlineService.offlineByTimeout(OFFLINE_THRESHOLD_SECONDS);
        if (count > 0) {
            log.warn("设备在线巡检：{} 台心跳超时置离线（阈值 {}s）", count, OFFLINE_THRESHOLD_SECONDS);
        }
    }
}
