package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceMetricsService;
import com.reason.modules.device.service.DeviceMonitorService;
import com.reason.modules.job.entity.ScheduleJobEntity;
import com.reason.modules.job.service.ScheduleJobLogService;
import com.reason.modules.job.service.ScheduleJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务指标采集（批次4 D-D 第 0 档）
 *
 * <p>第 0 档原则：不引 Prometheus/Micrometer 注册表——单体样例的运维视图由 DB/Redis
 * 实时派生即可。四项核心指标对应批次4 的"事故可判"：指令积压（通道面）、设备在线率
 * （心跳面）、任务最后成功时间（调度面，与看护同一数据源）、未处理告警数（处置面）。</p>
 */
@Slf4j
@Service("deviceMetricsService")
public class DeviceMetricsServiceImpl implements DeviceMetricsService {

    private final DeviceCommandLogService commandLogService;
    private final DeviceMonitorService monitorService;
    private final DeviceAlarmService alarmService;
    private final DeviceRecordDao recordDao;
    private final ScheduleJobService scheduleJobService;
    private final ScheduleJobLogService scheduleJobLogService;

    public DeviceMetricsServiceImpl(DeviceCommandLogService commandLogService,
                                    DeviceMonitorService monitorService,
                                    DeviceAlarmService alarmService,
                                    DeviceRecordDao recordDao,
                                    ScheduleJobService scheduleJobService,
                                    ScheduleJobLogService scheduleJobLogService) {
        this.commandLogService = commandLogService;
        this.monitorService = monitorService;
        this.alarmService = alarmService;
        this.recordDao = recordDao;
        this.scheduleJobService = scheduleJobService;
        this.scheduleJobLogService = scheduleJobLogService;
    }

    @Override
    public Map<String, Object> collect() {
        Map<String, Object> m = new LinkedHashMap<>();
        //1. 通道面：待到位流水积压（>0 持续增长 = 事件通道或设备侧出了问题）
        m.put("commandPending", commandLogService.countPending());
        //2. 心跳面：在线率（口径与离线扫描一致——分母=接入过的设备，未接入不计入）
        long provisioned = recordDao.selectCount(new LambdaQueryWrapper<DeviceRecordEntity>()
                .ne(DeviceRecordEntity::getDeviceState, DeviceState.NOT_CONNECTED.getCode()));
        int online = monitorService.countOnline();
        m.put("deviceProvisioned", provisioned);
        m.put("deviceOnline", online);
        m.put("onlineRate", provisioned == 0 ? "-" : String.format("%.1f%%", online * 100.0 / provisioned));
        //3. 处置面：未处理告警数
        m.put("unhandledAlarms", alarmService.countUnhandled());
        //4. 调度面：barrier 家族任务视图（与看护同一数据源——含看护自身，看护也可观测）
        m.put("barrierTasks", collectTaskViews());
        return m;
    }

    /**
     * barrier 家族任务视图：最后成功时间 + 距今秒数（null=从未成功）
     */
    private List<Map<String, Object>> collectTaskViews() {
        List<ScheduleJobEntity> jobs = scheduleJobService.list(new LambdaQueryWrapper<ScheduleJobEntity>()
                .likeRight(ScheduleJobEntity::getJobBean, "barrier")
                .orderByAsc(ScheduleJobEntity::getJobId));
        long nowSec = System.currentTimeMillis() / 1000;
        List<Map<String, Object>> views = new ArrayList<>();
        for (ScheduleJobEntity job : jobs) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("jobId", job.getJobId());
            v.put("jobName", job.getJobName());
            v.put("jobState", job.getJobState());
            Long lastOk = scheduleJobLogService.getLastSuccessTime(job.getJobId());
            v.put("lastSuccessTime", lastOk);
            v.put("lastSuccessAgoSeconds", lastOk == null ? null : nowSec - lastOk);
            views.add(v);
        }
        return views;
    }
}
