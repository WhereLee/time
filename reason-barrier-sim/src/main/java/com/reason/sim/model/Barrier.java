package com.reason.sim.model;

import com.reason.sim.reporter.EventReporter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 升降杆（物理杆的代码替身——"杆=执行者"对象）
 *
 * <p>职责只有两条：收指令就执行（动作非瞬时：置动作中 -> 耗时 -> 到位），
 * 状态变化必须上报。它不知道平台内部结构、不知道指令是谁发的——它只是一根杆。
 *
 * <p>动作合法性由杆自己裁决（状态机）：降下态才能升、升起态才能降、动作中拒绝新指令。
 * 裁决与状态置位在 synchronized 内原子完成；动作的耗时等待放在异步线程（不阻塞指令回执）。</p>
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

    /**
     * 指令入口：裁决合法性并启动动作（异步执行，立即返回）
     *
     * @throws IllegalStateException 动作在当前状态不合法（由平台透传给管理端）
     */
    public synchronized void execute(BarrierAction action) {
        //状态机裁决：当前状态允许这个动作吗？
        if (state == BarrierState.MOVING) {
            throw new IllegalStateException("动作进行中，请等待到位");
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
            target = BarrierState.DOWN;
        }
        //先置"动作中"（物理：杆已经开始动），再异步推进到位
        state = BarrierState.MOVING;
        log.info("[{}] 开始执行 {} -> {}（耗时 {}ms）", deviceNo, action, target, moveMillis);
        actionExecutor.submit(() -> runAction(target));
    }

    /**
     * 动作推进（异步线程）：先上报动作中 -> 耗时等待（模拟机械运动）-> 置目标态 -> 上报到位
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
        synchronized (this) {
            state = target;
        }
        reporter.report(deviceNo, target);
        log.info("[{}] 动作完成，当前状态={}", deviceNo, target);
    }
}
