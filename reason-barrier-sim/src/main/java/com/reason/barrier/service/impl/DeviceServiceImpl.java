package com.reason.barrier.service.impl;

import com.reason.barrier.config.TraceIds;
import com.reason.barrier.model.Barrier;
import com.reason.barrier.model.BarrierAction;
import com.reason.barrier.model.BarrierState;
import com.reason.barrier.registry.BarrierRegistry;
import com.reason.barrier.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备服务实现
 *
 * <p>业务编排：取设备（不存在 -> IllegalArgumentException）-> 交给 Barrier 裁决执行 ->
 * 受理/幂等忽略翻译为回执消息；动作不合法（IllegalStateException）不在此吞掉，
 * 向上传播由 controller 翻译为 code=1（设备拒绝必须让平台显式感知——契约修正：
 * 旧版拒绝也回 code=0，平台无法区分"受理"与"被拒"）。</p>
 */
@Slf4j
@Service("deviceService")
public class DeviceServiceImpl implements DeviceService {

    private final BarrierRegistry registry;

    public DeviceServiceImpl(BarrierRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String open(String deviceNo, long seq, String traceId) {
        return execute(deviceNo, BarrierAction.OPEN, seq, traceId);
    }

    @Override
    public String close(String deviceNo, long seq, String traceId) {
        return execute(deviceNo, BarrierAction.CLOSE, seq, traceId);
    }

    @Override
    public String injectFault(String deviceNo) {
        Barrier barrier = requireBarrier(deviceNo);
        barrier.injectFault();
        return "卡杆故障已注入：下一个动作将卡死转 FAULT";
    }

    @Override
    public String recover(String deviceNo) {
        Barrier barrier = requireBarrier(deviceNo);
        //设备自发事件（检修动作）：链路起点在设备侧——调用线程（注入口）已有 traceId 则沿用，无则生成
        barrier.recover(TraceIds.orGenerate(MDC.get(TraceIds.MDC_KEY)));
        return "人工复位完成（故障清除，杆已回落则上报 DOWN）";
    }

    @Override
    public String tamper(String deviceNo, String state) {
        Barrier barrier = requireBarrier(deviceNo);
        BarrierState forced;
        try {
            forced = BarrierState.valueOf(state);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法状态: " + state + "（外力改态只支持 UP/DOWN）");
        }
        //设备自发事件（物理世界外力）：调用线程已有 traceId 则沿用（注入口请求与事件上报同号），无则生成
        barrier.tamper(forced, TraceIds.orGenerate(MDC.get(TraceIds.MDC_KEY)));
        return "外力改态完成 -> " + forced + "（设备已主动上报）";
    }

    @Override
    public String setVehicle(String deviceNo, boolean present) {
        Barrier barrier = requireBarrier(deviceNo);
        barrier.setVehiclePresent(present);
        return present ? "防砸信号：杆下有车（CLOSE 将被互锁拒绝）" : "防砸信号：杆下无车（恢复可降）";
    }

    @Override
    public String setStuck(String deviceNo, boolean stuck) {
        Barrier barrier = requireBarrier(deviceNo);
        barrier.setStuck(stuck);
        return stuck ? "静默故障已注入（受理不动作不上报）" : "静默故障已清除";
    }

    @Override
    public String setStuckMoving(String deviceNo, boolean stuck) {
        Barrier barrier = requireBarrier(deviceNo);
        barrier.setStuckMoving(stuck);
        return stuck ? "卡动作中已注入（MOVING 后不终态）" : "卡动作中已清除";
    }

    @Override
    public Map<String, Object> queryState(String deviceNo) {
        //QUERY_STATE 应答源：设备实况快照（含代际/事件序/最近指令 seq——平台诊断与对账用）
        Barrier barrier = requireBarrier(deviceNo);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("deviceNo", barrier.getDeviceNo());
        snapshot.put("state", barrier.getState().getCode());
        snapshot.put("bootId", barrier.getBootId());
        snapshot.put("eventSeq", barrier.getEventSeq());
        snapshot.put("lastCommandSeq", barrier.getLastSeq());
        log.info("状态查询应答 deviceNo={} state={} bootId={} eventSeq={} lastCommandSeq={}",
                deviceNo, barrier.getState(), barrier.getBootId(),
                barrier.getEventSeq(), barrier.getLastSeq());
        return snapshot;
    }

    private String execute(String deviceNo, BarrierAction action, long seq, String traceId) {
        Barrier barrier = requireBarrier(deviceNo);
        //IllegalStateException（动作不合法/互锁/故障态）不捕获——传播给 controller 翻译为拒绝回执
        boolean accepted = barrier.execute(action, seq, traceId);
        if (accepted) {
            log.info("[指令受理] deviceNo={} action={} seq={}", deviceNo, action, seq);
            return "指令已受理";
        }
        //幂等忽略对平台也是成功：指令送达且设备保证不会重复动作（平台重试机制的安全网）
        log.info("[指令幂等忽略] deviceNo={} action={} seq={}（重复/乱序指令被设备吸收）", deviceNo, action, seq);
        return "重复指令已幂等忽略(seq=" + seq + ")";
    }

    private Barrier requireBarrier(String deviceNo) {
        Barrier barrier = registry.get(deviceNo);
        if (barrier == null) {
            throw new IllegalArgumentException("设备不存在: " + deviceNo);
        }
        return barrier;
    }
}
