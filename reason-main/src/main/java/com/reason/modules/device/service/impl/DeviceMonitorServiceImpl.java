package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.exception.RRException;
import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.config.BarrierRedisKeys;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceMonitorService;
import com.reason.modules.device.service.DeviceRecordService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 设备监控服务实现
 *
 * <p>心跳处理三步：对账 → 故障自述告警 → 刷新在线 key（TTL=心跳超时阈值）。
 * 对账只对稳定态且含两道豁免：台账 grace 窗口内刚被事件更新则让路（时序差非漂移）、
 * 首次接入只校正不告警（正常流程非异常）。在线判定 = EXISTS，离线判定 = key 过期，
 * 全程无扫库无时间戳比对——Redis TTL 就是业务逻辑本身。</p>
 */
@Slf4j
@Service("deviceMonitorService")
public class DeviceMonitorServiceImpl implements DeviceMonitorService {

    private final StringRedisTemplate stringRedisTemplate;
    private final BarrierProperties barrierProperties;
    private final DeviceRecordDao recordDao;
    private final DeviceRecordService deviceRecordService;
    private final DeviceAlarmService deviceAlarmService;
    private final DeviceCommandLogService commandLogService;

    /** 启动时刻（0.8：启动宽限期内不做离线判定——平台重启期间 TTL 自然过期，防全量误报） */
    private long startupTime;

    public DeviceMonitorServiceImpl(StringRedisTemplate stringRedisTemplate,
                                    BarrierProperties barrierProperties,
                                    DeviceRecordDao recordDao,
                                    DeviceRecordService deviceRecordService,
                                    DeviceAlarmService deviceAlarmService,
                                    DeviceCommandLogService commandLogService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.barrierProperties = barrierProperties;
        this.recordDao = recordDao;
        this.deviceRecordService = deviceRecordService;
        this.deviceAlarmService = deviceAlarmService;
        this.commandLogService = commandLogService;
    }

    @PostConstruct
    public void init() {
        this.startupTime = System.currentTimeMillis() / 1000;
        log.info("设备监控就绪：启动宽限期 {}s（期间不做离线判定）", barrierProperties.getOnlineStartupGraceSeconds());
    }

    @Override
    public void heartbeat(String deviceNo, Integer stateCode) {
        //1. 档案必须存在：未登记设备的心跳 = 配置错位（模拟器里有、平台没建档），显式失败
        DeviceRecordEntity record = recordDao.selectOne(new LambdaQueryWrapper<DeviceRecordEntity>()
                .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
        if (record == null) {
            throw new RRException("未登记的设备心跳: " + deviceNo);
        }

        //2. 状态对账：仅对稳定态（UP/DOWN/FAULT）——MOVING 是指令驱动的合法中间态，
        //心跳与事件两通道的时序差会造成瞬时不一致（如心跳先到、MOVING 事件在途），
        //那不是漂移；忽略中间态也防了陈旧心跳把台账从 UP 回退到 MOVING 的抖动
        boolean stable = stateCode != null && (stateCode == DeviceState.UP.getCode()
                || stateCode == DeviceState.DOWN.getCode()
                || stateCode == DeviceState.FAULT.getCode());
        if (stable && !stateCode.equals(record.getDeviceState())) {
            //时序让路守卫：台账刚被事件通道更新过（grace 窗口内）说明事件通道活跃，
            //本次心跳自述采样于更早时刻，不一致大概率是两通道时序差——本轮跳过，
            //下轮心跳若仍不一致（真漂移不会自愈）再校正，避免陈旧心跳把台账回退
            long nowSec = System.currentTimeMillis() / 1000;
            boolean recordFresh = record.getDeviceUpdatetime() != null
                    && nowSec - record.getDeviceUpdatetime() < barrierProperties.getReconcileGraceSeconds();
            if (recordFresh) {
                log.debug("心跳对账让路：台账 {}s 内刚被事件通道更新 deviceNo={} 台账={} 自述={}",
                        barrierProperties.getReconcileGraceSeconds(), deviceNo, record.getDeviceState(), stateCode);
            } else {
                //首次接入豁免：建档恒为未接入(0)，第一次心跳带真实状态是正常接入流程不是漂移，
                //只校正不告警（否则每台设备上线都误报一条 STATE_MISMATCH）
                boolean firstContact = record.getDeviceState() == null
                        || record.getDeviceState() == DeviceState.NOT_CONNECTED.getCode();
                log.warn("心跳对账发现状态{} deviceNo={} 台账={} 设备自述={} -> 以设备为准校正",
                        firstContact ? "变化(首次接入)" : "漂移", deviceNo, record.getDeviceState(), stateCode);
                //走事件驱动的唯一合法写入路径（心跳自述也是设备说的话，铁律不破）
                deviceRecordService.updateStateByEvent(deviceNo, stateCode);
                if (!firstContact) {
                    deviceAlarmService.raise(deviceNo, AlarmType.STATE_MISMATCH,
                            "心跳自述状态 " + stateCode + " 与台账 " + record.getDeviceState() + " 不一致，已按设备校正");
                }
            }
        }

        //3. 故障自述告警（设备说自己 FAULT——持续故障由去重窗口控制提醒频率）
        if (stateCode != null && stateCode == DeviceState.FAULT.getCode()) {
            deviceAlarmService.raise(deviceNo, AlarmType.DEVICE_FAULT, "设备心跳自述故障态(卡杆等)，需人工处置");
            //0.6 FAULT 双通道收口：心跳自述 FAULT 与事件通道 FAULT 同语义——在途指令执行中断
            //（此前只告警不中断：同一故障走心跳通道时流水停在 PENDING，monitor 会对故障设备重试
            // 至 RETRY_EXCEEDED——账本终态由"哪条通道先到"决定，收口后与通道无关）
            commandLogService.markExecFailed(deviceNo);
        }

        //4. 恢复反向标记（阶段1 实测补漏）：能收到心跳 = 设备活着 = 离线判定反转——
        //自动关闭该设备未处理 OFFLINE 告警（主节点宕机期间平台自收不到心跳而误报的离线，
        //恢复后不该留在未处理列表等人工；OFFLINE 关闭权威 = 心跳到达，见 markOnlineRecovered）
        deviceAlarmService.markOnlineRecovered(deviceNo);

        //5. 刷新在线 key（覆盖式 SET + TTL：每次心跳续命，停止心跳后 TTL 自然过期 = 离线）
        stringRedisTemplate.opsForValue().set(BarrierRedisKeys.ONLINE_PREFIX + deviceNo,
                String.valueOf(System.currentTimeMillis() / 1000),
                barrierProperties.getHeartbeatTimeoutSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public boolean isOnline(String deviceNo) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BarrierRedisKeys.ONLINE_PREFIX + deviceNo));
    }

    @Override
    public void scanOffline() {
        //0.8 启动宽限：平台重启期间心跳 TTL 自然过期，恢复后立即扫描会全量误报 OFFLINE——
        //宽限（>重启耗时+TTL）后再开始离线判定
        long nowSec = System.currentTimeMillis() / 1000;
        if (nowSec - startupTime < barrierProperties.getOnlineStartupGraceSeconds()) {
            log.debug("启动宽限期内跳过离线扫描（剩余 {}s）",
                    barrierProperties.getOnlineStartupGraceSeconds() - (nowSec - startupTime));
            return;
        }
        //只扫"接入过"的设备（状态≠未接入）：从未上线的设备没有心跳是常态，不算离线异常
        List<DeviceRecordEntity> records = recordDao.selectList(new LambdaQueryWrapper<DeviceRecordEntity>()
                .ne(DeviceRecordEntity::getDeviceState, DeviceState.NOT_CONNECTED.getCode()));
        for (DeviceRecordEntity record : records) {
            //样例设备量级逐个 EXISTS 足够；生产海量设备用 pipeline 批量判定（此处注明不实现）
            if (!isOnline(record.getDeviceNo())) {
                deviceAlarmService.raise(record.getDeviceNo(), AlarmType.OFFLINE,
                        "心跳超时(>" + barrierProperties.getHeartbeatTimeoutSeconds() + "s)未收到，判定离线");
            }
        }
    }
}
