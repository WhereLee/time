package com.reason.barrier.model;

import com.reason.barrier.config.TraceIds;
import com.reason.barrier.reporter.EventReporter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 升降杆（物理杆的代码替身——"杆=执行者"对象）
 *
 * <p>职责：收指令就执行（动作非瞬时：置动作中 -> 耗时 -> 到位），状态变化必须上报。
 * 它不知道平台内部结构、不知道指令是谁发的——它只是一根杆。</p>
 *
 * <p>杆自己扛住的边界（全部在设备侧裁决，平台只是被告知者）：
 * <ul>
 *   <li>动作合法性状态机：降下态才能升、升起态才能降、动作中拒绝新指令、故障态拒绝一切指令；</li>
 *   <li>指令幂等（seq）：seq <= lastSeq 视为重复/乱序指令直接忽略——平台超时重发同 seq
 *       不会让杆动两次，乱序到达的旧指令不会覆盖新动作；</li>
 *   <li>卡杆故障：faultInjected 时动作执行到一半卡死转 FAULT 并上报（失败显式化，不悄悄重试）；</li>
 *   <li>防砸互锁：杆下有车（vehiclePresent）时拒绝降杆——安全判断压过一切指令来源；</li>
 *   <li>外力改态：tamper 直接改物理状态并主动上报（设备被掰了要自己说，不替平台圆谎）；</li>
 *   <li>人工复位：recover 清故障，杆停在 FAULT 时复位后回落 DOWN 并上报。</li>
 * </ul></p>
 *
 * <p>并发纪律：裁决与状态置位在 synchronized 内原子完成；动作耗时等待与 HTTP 上报
 * 在异步线程且锁外执行（不阻塞指令回执，不在锁内做 IO）；动作完成时若发现状态已被
 * 外力改走（不再是 MOVING），动作结果作废——设备尊重物理世界发生的事。</p>
 */
@Slf4j
public class Barrier {

    private final String deviceNo;
    private final String name;
    private final long moveMillis;
    private final String bootId;
    private final EventReporter reporter;

    /** 动作执行线程：单线程按到达顺序执行动作（一根杆同一时刻只能做一个动作） */
    private final ExecutorService actionExecutor;

    /** 事件序号（协议 v2：设备内单调递增，平台凭 (bootId,eventSeq) 拒绝重放/乱序；重启后从 1 重计） */
    private final AtomicLong eventCounter = new AtomicLong();

    /** 当前物理状态（volatile：指令线程裁决 + 动作线程推进，跨线程可见） */
    private volatile BarrierState state = BarrierState.DOWN;

    /** 已受理的最大指令 seq（幂等去重线：seq <= lastSeq 的重复/乱序指令直接忽略） */
    private volatile long lastSeq = 0L;

    /** 卡杆故障注入标志（true 时下一个动作执行到一半卡死转 FAULT——扮演物理世界的意外） */
    private volatile boolean faultInjected = false;

    /** 静默故障注入标志（0.3 卡滞不终态：受理指令后无动作无上报——到位传感器失效/主控失聪） */
    private volatile boolean stuckInjected = false;

    /** 卡动作中注入标志（0.4：动作执行到 MOVING 后永不终态不上报——机械卡滞但没报故障） */
    private volatile boolean stuckMovingInjected = false;

    /** 最近被拒指令 seq（0.6 双线：被拒过的 seq 重试说真话——不再被幂等线吞成"假成功"） */
    private volatile long lastRejectedSeq = -1L;

    /** 防砸信号（杆下探测器：true=有车，CLOSE 被互锁拒绝） */
    private volatile boolean vehiclePresent = false;

    public Barrier(String deviceNo, String name, long moveMillis, EventReporter reporter, String bootId) {
        this.deviceNo = deviceNo;
        this.name = name;
        this.moveMillis = moveMillis;
        this.reporter = reporter;
        this.bootId = bootId;
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "barrier-" + deviceNo);
            t.setDaemon(true);
            return t;
        };
        this.actionExecutor = Executors.newSingleThreadExecutor(factory);
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public String getName() {
        return name;
    }

    public BarrierState getState() {
        return state;
    }

    /**
     * 设备当前代际（协议 v2：重启后变化——平台据此重置事件序基线，重启自述不被旧序误拒）
     */
    public String getBootId() {
        return bootId;
    }

    /**
     * 已发出的事件序号（协议 v2：诊断/应答用——平台 QUERY_STATE 可问）
     */
    public long getEventSeq() {
        return eventCounter.get();
    }

    /**
     * 已受理的最大指令 seq（诊断/应答用——平台判断在途指令是否已被更新指令覆盖）
     */
    public long getLastSeq() {
        return lastSeq;
    }

    public boolean isFaultInjected() {
        return faultInjected;
    }

    public boolean isVehiclePresent() {
        return vehiclePresent;
    }

    /**
     * 指令入口（带 seq 幂等）：裁决合法性并启动动作（异步执行，立即返回）
     *
     * @param seq     平台指令序号（设备内单调递增；重发同 seq = 幂等忽略）
     * @param traceId 链路跟踪号（平台下发携带，沿用到动作线程与事件上报——批次1 D-E）
     * @return true=受理执行；false=重复/乱序指令已幂等忽略（对平台而言也是"成功受理"）
     * @throws IllegalStateException 动作在当前状态不合法（由 service 翻译后回执平台）
     */
    public synchronized boolean execute(BarrierAction action, long seq, String traceId) {
        //0.6 双线 seq：曾被拒绝的指令重试 -> 明确拒绝（状态不允许，重试无意义）——
        //平台据此终止流水（SEND_FAILED），不再被幂等线吞成"已执行"假成功
        if (seq == lastRejectedSeq) {
            throw new IllegalStateException("该指令 seq=" + seq + " 曾被设备拒绝(状态不允许)，重试无意义");
        }
        //幂等去重：平台超时重试的同 seq 指令、乱序到达的旧指令，都在此被吸收——杆不会动两次
        if (seq <= lastSeq) {
            log.info("[{}] 重复/乱序指令幂等忽略 seq={} (lastSeq={})", deviceNo, seq, lastSeq);
            return false;
        }
        //状态机裁决
        if (state == BarrierState.MOVING) {
            reject(seq, "动作进行中，请等待到位");
        }
        if (state == BarrierState.FAULT) {
            reject(seq, "设备故障中，需人工复位后才接受指令");
        }
        BarrierState target;
        if (action == BarrierAction.OPEN) {
            if (state == BarrierState.UP) {
                reject(seq, "已处于升起状态");
            }
            target = BarrierState.UP;
        } else {
            if (state == BarrierState.DOWN) {
                reject(seq, "已处于降下状态");
            }
            //防砸互锁：杆下有车拒绝降杆——安全判断压过指令来源（手动/自动/重试一视同仁）
            if (vehiclePresent) {
                reject(seq, "杆下有车，防砸互锁拒绝降杆");
            }
            target = BarrierState.DOWN;
        }
        //受理：推进幂等线
        lastSeq = seq;
        //0.3 静默故障注入：受理但无动作无上报（状态保持原样）——平台侧连续校正将触发 AutoTask 熔断
        if (stuckInjected) {
            log.error("[{}] 静默故障注入：指令已受理但无动作无上报 seq={}（设备主控失聪）", deviceNo, seq);
            return true;
        }
        //置"动作中"（物理：杆已经开始动），再异步推进到位
        state = BarrierState.MOVING;
        log.info("[{}] 开始执行 {} -> {}（seq={} 耗时 {}ms）", deviceNo, action, target, seq, moveMillis);
        actionExecutor.submit(() -> runAction(target, seq, traceId));
        return true;
    }

    /**
     * 状态机拒绝：记录被拒 seq（0.6 双线）后抛出（拒绝不推进幂等线）
     */
    private void reject(long seq, String reason) {
        lastRejectedSeq = seq;
        throw new IllegalStateException(reason);
    }

    /**
     * 动作推进（异步线程）：上报动作中 -> 耗时等待（模拟机械运动）-> 到位/卡杆 -> 上报结果。
     * 事件携带引起动作的 commandSeq（协议 v2：平台凭它按 seq 精确销账）。
     * traceId 显式传入并置 MDC（跨线程边界：HTTP 线程的 MDC 不会自动传到动作线程）
     */
    private void runAction(BarrierState target, long commandSeq, String traceId) {
        MDC.put(TraceIds.MDC_KEY, traceId);
        try {
            reportEvent(BarrierState.MOVING, commandSeq, traceId);
            try {
                Thread.sleep(moveMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] 动作被中断", deviceNo);
                return;
            }
            boolean faulted;
            synchronized (this) {
                //动作期间状态被外力改走（tamper/recover）：动作结果作废，尊重物理世界发生的事
                if (state != BarrierState.MOVING) {
                    log.warn("[{}] 动作期间状态被外力改变(当前={})，本次动作结果作废", deviceNo, state);
                    return;
                }
                //0.4 卡动作中注入：MOVING 后永不终态不上报（机械卡滞但未报故障）——
                //台账将卡 MOVING，由平台巡检(MOVING_STUCK 告警)显式化，不在此替平台圆谎
                if (stuckMovingInjected) {
                    log.error("[{}] 卡动作中注入：杆停在 MOVING 不终态不上报（机械卡滞，等巡检告警交人工）", deviceNo);
                    return;
                }
                faulted = faultInjected;
                state = faulted ? BarrierState.FAULT : target;
            }
            //上报在锁外（HTTP IO 不进临界区）
            if (faulted) {
                log.warn("[{}] 动作卡死（故障注入）-> FAULT，等待人工复位", deviceNo);
                reportEvent(BarrierState.FAULT, commandSeq, traceId);
            } else {
                reportEvent(target, commandSeq, traceId);
                log.info("[{}] 动作完成，当前状态={}", deviceNo, target);
            }
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    /**
     * 故障注入（扮演物理世界的意外）：下一个动作将执行到一半卡死转 FAULT
     */
    public synchronized void injectFault() {
        faultInjected = true;
        log.warn("[{}] 卡杆故障已注入：下一个动作将卡死转 FAULT", deviceNo);
    }

    /**
     * 静默故障注入（0.3 卡滞不终态：到位传感器失效/主控失聪——受理但无动作无上报，
     * 状态保持原样；平台连续校正未闭环将触发 AutoTask 熔断）
     */
    public synchronized void setStuck(boolean stuck) {
        this.stuckInjected = stuck;
        log.error("[{}] 静默故障注入置为 {}（受理不动作不上报——验证平台自动校正熔断）", deviceNo, stuck);
    }

    /**
     * 卡动作中注入（0.4：机械卡滞但没报故障——动作到 MOVING 后永不终态不上报，
     * 台账卡 MOVING，验证平台巡检告警）
     */
    public synchronized void setStuckMoving(boolean stuck) {
        this.stuckMovingInjected = stuck;
        log.error("[{}] 卡动作中注入置为 {}（MOVING 后不终态——验证平台 MOVING 巡检）", deviceNo, stuck);
    }

    public boolean isStuckInjected() {
        return stuckInjected;
    }

    public boolean isStuckMovingInjected() {
        return stuckMovingInjected;
    }

    /**
     * 人工复位：清除全部注入（故障/静默/卡动作中）；若杆停在 FAULT，复位后回落 DOWN 并上报（检修完成语义）
     *
     * @param traceId 链路跟踪号（设备自发事件，由调用方生成）
     */
    public void recover(String traceId) {
        boolean needReport;
        synchronized (this) {
            faultInjected = false;
            stuckInjected = false;
            stuckMovingInjected = false;
            needReport = (state == BarrierState.FAULT);
            if (needReport) {
                state = BarrierState.DOWN;
            }
        }
        if (needReport) {
            log.info("[{}] 人工复位：故障清除，杆回落 DOWN", deviceNo);
            //复位是非指令驱动状态变化：commandSeq=null（协议 v2——平台只更新台账，不销任何流水）
            reportEvent(BarrierState.DOWN, null, traceId);
        } else {
            log.info("[{}] 故障注入标志已清除（杆未处于故障态）", deviceNo);
        }
    }

    /**
     * 外力改态：物理世界把杆掰到某稳定态（不经任何指令）——设备感知后主动上报，
     * 平台心跳/事件对账随之校正（"外力改态被周期对账吸收"边界的设备侧配合）
     *
     * @param traceId 链路跟踪号（设备自发事件，由调用方生成）
     */
    public void tamper(BarrierState forced, String traceId) {
        if (forced != BarrierState.UP && forced != BarrierState.DOWN) {
            throw new IllegalArgumentException("外力改态只支持稳定态 UP/DOWN");
        }
        synchronized (this) {
            state = forced;
        }
        log.warn("[{}] 外力改态 -> {}（非指令驱动，设备自述上报）", deviceNo, forced);
        //外力改态是非指令驱动状态变化：commandSeq=null（协议 v2——平台只更新台账，不销任何流水）
        reportEvent(forced, null, traceId);
    }

    /**
     * 防砸信号更新：杆下探测器检测到车进出（true=有车，此后 CLOSE 被互锁拒绝）
     */
    public void setVehiclePresent(boolean present) {
        vehiclePresent = present;
        log.info("[{}] 防砸信号更新：杆下{}车", deviceNo, present ? "有" : "无");
    }

    /**
     * 上报一次状态变化（协议 v2 事件载荷：设备内单调 eventSeq + 本进程 bootId + 链路 traceId）
     */
    private void reportEvent(BarrierState reported, Long commandSeq, String traceId) {
        long eventSeq = eventCounter.incrementAndGet();
        reporter.report(deviceNo, reported, commandSeq, bootId, eventSeq, traceId);
    }
}
