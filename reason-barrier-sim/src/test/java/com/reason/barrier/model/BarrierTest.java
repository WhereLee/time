package com.reason.barrier.model;

import com.reason.barrier.reporter.EventReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 升降杆状态机测试（杆=执行者的对象行为）：
 * 动作合法性裁决（降才能升/升才能降/动作中拒绝）+ 非瞬时动作链（MOVING -> 到位）+ 状态上报
 */
@DisplayName("升降杆状态机")
class BarrierTest {

    /** 记录型假上报通道（不碰 HTTP） */
    private static class FakeReporter implements EventReporter {
        final List<BarrierState> reported = new ArrayList<>();

        @Override
        public void report(String deviceNo, BarrierState state) {
            reported.add(state);
        }
    }

    private Barrier newBarrier(FakeReporter reporter, long moveMillis) {
        return new Barrier("BARRIER-TEST", "测试杆", moveMillis, reporter);
    }

    @Test
    @DisplayName("初始为降下态，降下态允许升起")
    void 初始降下_可升起() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 50);
        assertThat(barrier.getState()).isEqualTo(BarrierState.DOWN);

        //execute 异步执行，轮询等待到位
        barrier.execute(BarrierAction.OPEN);
        waitForState(barrier, BarrierState.UP);

        //完整动作链：动作中 -> 到位，且每段都上报了
        assertThat(reporter.reported).containsExactly(BarrierState.MOVING, BarrierState.UP);
    }

    @Test
    @DisplayName("升起态不允许再升（状态机拒绝）")
    void 升起态_拒绝再升() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN);
        waitForState(barrier, BarrierState.UP);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.OPEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("升起");
    }

    @Test
    @DisplayName("升起态允许降下；降下态不允许再降")
    void 升降闭环_拒绝重复降() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        //升上去
        barrier.execute(BarrierAction.OPEN);
        waitForState(barrier, BarrierState.UP);
        //降下来
        barrier.execute(BarrierAction.CLOSE);
        waitForState(barrier, BarrierState.DOWN);
        //已降下，再降被拒
        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("降下");
    }

    @Test
    @DisplayName("动作进行中拒绝新指令（MOVING 态互斥）")
    void 动作中_拒绝新指令() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 2000); //长耗时保证处于 MOVING
        barrier.execute(BarrierAction.OPEN);
        assertThat(barrier.getState()).isEqualTo(BarrierState.MOVING);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("进行中");
    }

    /** 轮询等待目标状态（单测不做 sleep 硬等，最多 2 秒） */
    private void waitForState(Barrier barrier, BarrierState target) {
        long deadline = System.currentTimeMillis() + 2000;
        while (barrier.getState() != target && System.currentTimeMillis() < deadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(barrier.getState()).isEqualTo(target);
    }
}
