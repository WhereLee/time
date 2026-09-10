package com.reason.modules.device.service;

import java.util.Map;

/**
 * 设备域业务指标（批次4 D-D 第 0 档）
 *
 * <p>DB/Redis 实时派生：指令积压（PENDING）/在线率/任务最后成功时间/未处理告警数——
 * 对应批次4 的"事故可判"：积压看通道、在线率看心跳面、任务时间看调度面、告警数看处置面。</p>
 */
public interface DeviceMetricsService {

    /**
     * 采集当前指标快照（实时派生，无缓存——调用频率低，值必须新鲜）
     */
    Map<String, Object> collect();
}
