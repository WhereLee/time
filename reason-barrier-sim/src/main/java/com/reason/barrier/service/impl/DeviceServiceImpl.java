package com.reason.barrier.service.impl;

import com.reason.barrier.model.Barrier;
import com.reason.barrier.model.BarrierAction;
import com.reason.barrier.registry.BarrierRegistry;
import com.reason.barrier.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 设备服务实现
 *
 * <p>业务编排：取设备（不存在 -> 显式拒绝）-> 交给 Barrier 状态机裁决并启动动作 ->
 * 动作合法性异常翻译为回执消息（动作由 Barrier 异步执行，此处立即返回"已受理"）。</p>
 */
@Slf4j
@Service("deviceService")
public class DeviceServiceImpl implements DeviceService {

    private final BarrierRegistry registry;

    public DeviceServiceImpl(BarrierRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String open(String deviceNo) {
        return execute(deviceNo, BarrierAction.OPEN);
    }

    @Override
    public String close(String deviceNo) {
        return execute(deviceNo, BarrierAction.CLOSE);
    }

    private String execute(String deviceNo, BarrierAction action) {
        Barrier barrier = registry.get(deviceNo);
        if (barrier == null) {
            throw new IllegalArgumentException("设备不存在: " + deviceNo);
        }
        try {
            barrier.execute(action);
            log.info("[指令受理] deviceNo={} action={}", deviceNo, action);
            return "指令已受理";
        } catch (IllegalStateException e) {
            //动作在当前状态不合法（已升起再升/动作中再发指令等）——状态机裁决结果，翻译为回执
            log.warn("[指令拒绝] deviceNo={} action={} reason={}", deviceNo, action, e.getMessage());
            return e.getMessage();
        }
    }
}
