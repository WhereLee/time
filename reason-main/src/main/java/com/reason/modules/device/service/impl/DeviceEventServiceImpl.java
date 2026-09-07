package com.reason.modules.device.service.impl;

import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceEventService;
import com.reason.modules.device.service.DeviceRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 设备事件处理编排实现
 *
 * <p>顺序有意为之：先台账（设备说的话立即成为快照）→ 再流水（指令闭环的账）→ 再告警（喊人）。
 * 台账更新失败（未登记设备）会抛错中断——事件处理不做"半截子"：账本和告警都以台账更新成功为前提。</p>
 */
@Slf4j
@Service("deviceEventService")
public class DeviceEventServiceImpl implements DeviceEventService {

    private final DeviceRecordService deviceRecordService;
    private final DeviceCommandLogService commandLogService;
    private final DeviceAlarmService deviceAlarmService;

    public DeviceEventServiceImpl(DeviceRecordService deviceRecordService,
                                  DeviceCommandLogService commandLogService,
                                  DeviceAlarmService deviceAlarmService) {
        this.deviceRecordService = deviceRecordService;
        this.commandLogService = commandLogService;
        this.deviceAlarmService = deviceAlarmService;
    }

    @Override
    public void handleStateEvent(String deviceNo, int stateCode) {
        //1. 台账更新（反馈闭环铁律的唯一写入路径）
        deviceRecordService.updateStateByEvent(deviceNo, stateCode);

        //2. 按状态语义推进指令流水
        if (stateCode == DeviceState.UP.getCode()) {
            //升到位：OPEN 指令闭环（未命中流水 = 非指令驱动的状态变化，如外力改态后设备自报，正常）
            commandLogService.markArrived(deviceNo, "OPEN");
        } else if (stateCode == DeviceState.DOWN.getCode()) {
            commandLogService.markArrived(deviceNo, "CLOSE");
        } else if (stateCode == DeviceState.FAULT.getCode()) {
            //故障 = 执行中断：在途指令永远不会到位，账本显式记 EXEC_FAILED（不再重试）+ 告警交人工
            commandLogService.markExecFailed(deviceNo);
            deviceAlarmService.raise(deviceNo, AlarmType.DEVICE_FAULT,
                    "设备上报故障态(卡杆等)，在途指令已中断，需人工处置后复位");
        }
        //MOVING 中间态：只更新台账，流水继续等到位事件
    }
}
