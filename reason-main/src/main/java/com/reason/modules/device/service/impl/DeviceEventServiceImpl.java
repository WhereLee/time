package com.reason.modules.device.service.impl;

import com.reason.modules.device.enums.AlarmType;
import com.reason.modules.device.enums.DeviceState;
import com.reason.modules.device.form.DeviceEventForm;
import com.reason.modules.device.service.DeviceAlarmService;
import com.reason.modules.device.service.DeviceCommandLogService;
import com.reason.modules.device.service.DeviceEventService;
import com.reason.modules.device.service.DeviceRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 设备事件处理编排实现（协议 v2）
 *
 * <p>顺序有意为之：先台账（设备说的话立即成为快照，带序守卫）→ 再流水（按证据精确闭环）→
 * 再告警（喊人）。台账更新失败（未登记设备）抛错中断；同代际旧序事件（重放/乱序迟到）被
 * 序守卫拒绝后幂等丢弃（false），不推进任何流水——事件处理不做"半截子"。</p>
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
    public void handleStateEvent(DeviceEventForm form) {
        //状态码合法性校验（未知码=协议错，快速失败）
        int stateCode = DeviceState.fromCode(form.getState()).getCode();
        String deviceNo = form.getDeviceNo();
        Long commandSeq = form.getCommandSeq();

        //1. 台账更新（协议 v2 序守卫：同代际旧序/重放 → false，幂等丢弃不推进流水）
        boolean accepted = deviceRecordService.updateStateByEventWithSeq(
                deviceNo, stateCode, form.getBootId(), form.getEventSeq());
        if (!accepted) {
            return;
        }

        //2. 按事件-动作映射推进流水（销账必须证据驱动：只有事件携带 commandSeq 才按 seq 精确销账）
        if (stateCode == DeviceState.UP.getCode()) {
            if (commandSeq != null) {
                commandLogService.markArrivedBySeq(deviceNo, commandSeq, "OPEN");
            }
            //commandSeq 空 = 外力改态到位：只更新台账，不动流水（无指令可销）
            //设备恢复运动（UP/DOWN 到位）= 状态类告警自动关闭（0.4：离线/卡死/自动校正失败随恢复标记）
            deviceAlarmService.markRecovered(deviceNo);
        } else if (stateCode == DeviceState.DOWN.getCode()) {
            if (commandSeq != null) {
                commandLogService.markArrivedBySeq(deviceNo, commandSeq, "CLOSE");
            }
            deviceAlarmService.markRecovered(deviceNo);
        } else if (stateCode == DeviceState.FAULT.getCode()) {
            //故障 = 执行中断：在途指令永远不会到位，账本显式记 EXEC_FAILED（不再重试）+ 告警交人工
            if (commandSeq != null) {
                //0.6 按 seq 归属中断（协议 v2 §3.2）：事件携带引起故障的 seq，精确中断该条——
                //手动+自动双触发源交叉窗口下，跨动作多条在途不再全断，故障归因到具体指令
                commandLogService.markExecFailedBySeq(deviceNo, commandSeq);
            } else {
                //无 seq 的故障（外力/未知源）：设备级全断（心跳通道 FAULT 同此语义）
                commandLogService.markExecFailed(deviceNo);
            }
            deviceAlarmService.raise(deviceNo, AlarmType.DEVICE_FAULT,
                    "设备上报故障态(卡杆等)，在途指令已中断，需人工处置后复位");
        }
        //MOVING 中间态：只更新台账，流水继续等到位事件
    }
}
