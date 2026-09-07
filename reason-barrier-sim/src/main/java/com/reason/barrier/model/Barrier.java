package com.reason.barrier.model;

import com.reason.barrier.reporter.EventReporter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

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
    private final EventReporter reporter;

    /** 动作执行线程：单线程按到达顺序执行动作（一根杆同一时刻只能做一个动作） */
    private final ExecutorService actionExecutor;

    /** 当前物理状态（volatile：指令线程裁决 + 动作线程推进，跨线程可见） */
    private volatile BarrierState state = BarrierState.DOWN;

    /** 已受理的最大指令 seq（幂等去重线：seq <= lastSeq 的重复/乱序指令直接忽略） */
    private volatile long lastSeq = 0L;

    /** 卡杆故障注入标志（true 时下一个动作执行到一半卡死转 FAULT——扮演物理世界的意外） */
    private volatile boolean faultInjected = false;

    /** 防砸信号（杆下探测器：true=有车，CLOSE 被互锁拒绝） */
    private volatile boolean vehiclePresent = false;

    public Barrier(String deviceNo, String name, long moveMillis, EventReporter reporter) {
        this.deviceNo = deviceNo;
        this.name = name;
        this.moveMillis = moveMillis;
        this.reporter = reporter;
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

    public boolean isFaultInjected() {
        return faultInjected;
    }

    public boolean isVehiclePresent() {
        return vehiclePresent;
    }

    /**
     * 指令入口（带 seq 幂等）：裁决合法性并启动动作（异步执行，立即返回）
     *
     * @param seq 平台指令序号（设备内单调递增；重发同 seq = 幂等忽略）
     * @return true=受理执行；false=重复/乱序指令已幂等忽略（对平台而言也是"成功受理"）
     * @throws IllegalStateException 动作在当前状态不合法（由 service 翻译后回执平台）
     */
    public synchronized boolean execute(BarrierAction action, long seq) {
        //幂等去重：平台超时重试的同 seq 指令、乱序到达的旧指令，都在此被吸收——杆不会动两次
        if (seq <= lastSeq) {
            log.info("[{}] 重复/乱序指令幂等忽略 seq={} (lastSeq={})", deviceNo, seq, lastSeq);
            return false;
        }
        //状态机裁决
        if (state == BarrierState.MOVING) {
            throw new IllegalStateException("动作进行中，请等待到位");
        }
        if (state == BarrierState.FAULT) {
            throw new IllegalStateException("设备故障中，需人工复位后才接受指令");
        }
        BarrierState target;
        if (action == BarrierAction.OPEN) {
            if (state == BarrierState.UP) {
                throw new IllegalStateException("已处于升起状态");
            }
            target = BarrierState.UP;
        } else {
            if (state == BarrierState.DOWN) {
                throw new IllegalStateException("已处于降下状态");
            }
            //防砸互锁：杆下有车拒绝降杆——安全判断压过指令来源（手动/自动/重试一视同仁）
            if (vehiclePresent) {
                throw new IllegalStateException("杆下有车，防砸互锁拒绝降杆");
            }
            target = BarrierState.DOWN;
        }
        //受理：推进幂等线 + 置"动作中"（物理：杆已经开始动），再异步推进到位
        lastSeq = seq;
        state = BarrierState.MOVING;
        log.info("[{}] 开始执行 {} -> {}（seq={} 耗时 {}ms）", deviceNo, action, target, seq, moveMillis);
        actionExecutor.submit(() -> runAction(target));
        return true;
    }

    /**
     * 动作推进（异步线程）：上报动作中 -> 耗时等待（模拟机械运动）-> 到位/卡杆 -> 上报结果
     */
    private void runAction(BarrierState target) {
        reporter.report(deviceNo, BarrierState.MOVING);
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
            faulted = faultInjected;
            state = faulted ? BarrierState.FAULT : target;
        }
        //上报在锁外（HTTP IO 不进临界区）
        if (faulted) {
            log.warn("[{}] 动作卡死（故障注入）-> FAULT，等待人工复位", deviceNo);
            reporter.report(deviceNo, BarrierState.FAULT);
        } else {
            reporter.report(deviceNo, target);
            log.info("[{}] 动作完成，当前状态={}", deviceNo, target);
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
     * 人工复位：清除故障注入；若杆停在 FAULT，复位后回落 DOWN 并上报（检修完成语义）
     */
    public void recover() {
        boolean needReport;
        synchronized (this) {
            faultInjected = false;
            needReport = (state == BarrierState.FAULT);
            if (needReport) {
                state = BarrierState.DOWN;
            }
        }
        if (needReport) {
            log.info("[{}] 人工复位：故障清除，杆回落 DOWN", deviceNo);
            reporter.report(deviceNo, BarrierState.DOWN);
        } else {
            log.info("[{}] 故障注入标志已清除（杆未处于故障态）", deviceNo);
        }
    }

    /**
     * 外力改态：物理世界把杆掰到某稳定态（不经任何指令）——设备感知后主动上报，
     * 平台心跳/事件对账随之校正（"外力改态被周期对账吸收"边界的设备侧配合）
     */
    public void tamper(BarrierState forced) {
        if (forced != BarrierState.UP && forced != BarrierState.DOWN) {
            throw new IllegalArgumentException("外力改态只支持稳定态 UP/DOWN");
        }
        synchronized (this) {
            state = forced;
        }
        log.warn("[{}] 外力改态 -> {}（非指令驱动，设备自述上报）", deviceNo, forced);
        reporter.report(deviceNo, forced);
    }

    /**
     * 防砸信号更新：杆下探测器检测到车进出（true=有车，此后 CLOSE 被互锁拒绝）
     */
    public void setVehiclePresent(boolean present) {
        vehiclePresent = present;
        log.info("[{}] 防砸信号更新：杆下{}车", deviceNo, present ? "有" : "无");
    }
}
