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
 * 升降杆状态机测试（杆=执行者的对象行为，覆盖全部设备侧边界）：
 * 动作合法性裁决 + 非瞬时动作链 + seq 幂等/乱序吸收 + 防砸互锁 +
 * 卡杆故障 + 故障态拒令 + 人工复位 + 外力改态
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
    @DisplayName("初始为降下态，降下态允许升起（完整动作链 MOVING->UP 均上报）")
    void 初始降下_可升起() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 50);
        assertThat(barrier.getState()).isEqualTo(BarrierState.DOWN);

        //execute 异步执行，轮询等待到位
        assertThat(barrier.execute(BarrierAction.OPEN, 1)).isTrue();
        waitForState(barrier, BarrierState.UP);

        //完整动作链：动作中 -> 到位，且每段都上报了
        assertThat(reporter.reported).containsExactly(BarrierState.MOVING, BarrierState.UP);
    }

    @Test
    @DisplayName("升起态不允许再升（状态机拒绝）")
    void 升起态_拒绝再升() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN, 1);
        waitForState(barrier, BarrierState.UP);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.OPEN, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("升起");
    }

    @Test
    @DisplayName("升起态允许降下；降下态不允许再降")
    void 升降闭环_拒绝重复降() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN, 1);
        waitForState(barrier, BarrierState.UP);
        barrier.execute(BarrierAction.CLOSE, 2);
        waitForState(barrier, BarrierState.DOWN);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("降下");
    }

    @Test
    @DisplayName("动作进行中拒绝新指令（MOVING 态互斥）")
    void 动作中_拒绝新指令() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 2000); //长耗时保证处于 MOVING
        barrier.execute(BarrierAction.OPEN, 1);
        assertThat(barrier.getState()).isEqualTo(BarrierState.MOVING);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("进行中");
    }

    @Test
    @DisplayName("seq 幂等：重复 seq 与乱序旧 seq 都被吸收，杆不会动两次")
    void seq幂等_重复与乱序被吸收() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        assertThat(barrier.execute(BarrierAction.OPEN, 5)).isTrue();
        waitForState(barrier, BarrierState.UP);

        //平台超时重发同 seq=5：幂等忽略（返回 false），不触发任何动作与上报
        assertThat(barrier.execute(BarrierAction.OPEN, 5)).isFalse();
        //乱序到达的旧 seq=3（即使是合法动作 CLOSE）：同样被吸收
        assertThat(barrier.execute(BarrierAction.CLOSE, 3)).isFalse();
        assertThat(barrier.getState()).isEqualTo(BarrierState.UP);
        assertThat(reporter.reported).containsExactly(BarrierState.MOVING, BarrierState.UP);

        //更大的新 seq 正常受理
        assertThat(barrier.execute(BarrierAction.CLOSE, 6)).isTrue();
        waitForState(barrier, BarrierState.DOWN);
    }

    @Test
    @DisplayName("防砸互锁：杆下有车拒绝降杆，车走后恢复可降")
    void 防砸互锁_杆下有车拒绝降杆() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN, 1);
        waitForState(barrier, BarrierState.UP);

        //杆下探测器报有车：降杆被安全互锁拒绝（压过一切指令来源）
        barrier.setVehiclePresent(true);
        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("防砸");
        assertThat(barrier.getState()).isEqualTo(BarrierState.UP);

        //车走了：恢复可降
        barrier.setVehiclePresent(false);
        assertThat(barrier.execute(BarrierAction.CLOSE, 3)).isTrue();
        waitForState(barrier, BarrierState.DOWN);
    }

    @Test
    @DisplayName("卡杆故障：注入后动作执行到一半卡死转 FAULT 并上报")
    void 卡杆故障_动作中转FAULT() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 50);
        barrier.injectFault();

        assertThat(barrier.execute(BarrierAction.OPEN, 1)).isTrue();
        waitForState(barrier, BarrierState.FAULT);

        //上报链：动作中 -> 故障（失败显式化，设备主动喊）
        assertThat(reporter.reported).containsExactly(BarrierState.MOVING, BarrierState.FAULT);
    }

    @Test
    @DisplayName("故障态拒绝一切指令（停自动，等人工）")
    void 故障态_拒绝指令() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.injectFault();
        barrier.execute(BarrierAction.OPEN, 1);
        waitForState(barrier, BarrierState.FAULT);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.OPEN, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("故障");
        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("故障");
    }

    @Test
    @DisplayName("人工复位：故障清除，杆回落 DOWN 并上报；复位后接受新指令")
    void 人工复位_回落并恢复() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.injectFault();
        barrier.execute(BarrierAction.OPEN, 1);
        waitForState(barrier, BarrierState.FAULT);

        barrier.recover();
        assertThat(barrier.getState()).isEqualTo(BarrierState.DOWN);
        assertThat(barrier.isFaultInjected()).isFalse();
        //复位回落也是一次状态变化：必须上报（平台台账随设备回正）
        assertThat(reporter.reported).containsExactly(
                BarrierState.MOVING, BarrierState.FAULT, BarrierState.DOWN);

        //复位后恢复正常接受指令
        assertThat(barrier.execute(BarrierAction.OPEN, 2)).isTrue();
        waitForState(barrier, BarrierState.UP);
    }

    @Test
    @DisplayName("外力改态：物理世界掰杆，设备感知后主动上报；只支持稳定态")
    void 外力改态_主动上报() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);

        barrier.tamper(BarrierState.UP);
        assertThat(barrier.getState()).isEqualTo(BarrierState.UP);
        assertThat(reporter.reported).containsExactly(BarrierState.UP);

        //外力只能掰到稳定态（MOVING/FAULT 是设备自己的执行态，不接受外部指定）
        assertThatThrownBy(() -> barrier.tamper(BarrierState.MOVING))
                .isInstanceOf(IllegalArgumentException.class);
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
