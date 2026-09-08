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

    /** 链路号测试常量（批次1：execute/tamper/recover 均需传 traceId，验证其随事件上报透传） */
    private static final String TRACE = "test-trace-0001";

    /** 记录型假上报通道（不碰 HTTP；协议 v2：记录 commandSeq/bootId/eventSeq 载荷 + 批次1 traceId） */
    private static class FakeReporter implements EventReporter {
        final List<BarrierState> reported = new ArrayList<>();
        final List<Long> commandSeqs = new ArrayList<>();
        String lastBootId;
        long lastEventSeq;
        String lastTraceId;

        @Override
        public void report(String deviceNo, BarrierState state, Long commandSeq, String bootId, long eventSeq, String traceId) {
            reported.add(state);
            commandSeqs.add(commandSeq);
            lastBootId = bootId;
            lastEventSeq = eventSeq;
            lastTraceId = traceId;
        }
    }

    private Barrier newBarrier(FakeReporter reporter, long moveMillis) {
        return new Barrier("BARRIER-TEST", "测试杆", moveMillis, reporter, "boot-test-001");
    }

    @Test
    @DisplayName("初始为降下态，降下态允许升起（完整动作链 MOVING->UP 均上报）")
    void 初始降下_可升起() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 50);
        assertThat(barrier.getState()).isEqualTo(BarrierState.DOWN);

        //execute 异步执行，轮询等待到位
        assertThat(barrier.execute(BarrierAction.OPEN, 1, TRACE)).isTrue();
        waitForState(barrier, BarrierState.UP);

        //完整动作链：动作中 -> 到位，且每段都上报了（锁内置位与锁外上报有间隙，轮询等齐）
        waitForExactly(reporter, BarrierState.MOVING, BarrierState.UP);
        //协议 v2 载荷：指令驱动的事件携带 commandSeq=1（平台凭它按 seq 精确销账）
        assertThat(reporter.commandSeqs).containsExactly(1L, 1L);
        assertThat(reporter.lastBootId).isEqualTo("boot-test-001");
        assertThat(reporter.lastEventSeq).isEqualTo(2L); //事件序号单调递增 1,2
    }

    @Test
    @DisplayName("协议v2载荷：外力改态事件 commandSeq=null；动作中外部干扰不上报伪结果")
    void 协议v2载荷_外力改态无指令引用() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForState(barrier, BarrierState.UP);

        //外力改态（tamper）是非指令驱动：commandSeq=null，平台只更新台账不销任何流水
        barrier.tamper(BarrierState.DOWN, TRACE);
        waitForExactly(reporter, BarrierState.MOVING, BarrierState.UP, BarrierState.DOWN);
        assertThat(reporter.commandSeqs).containsExactly(1L, 1L, null);
        assertThat(reporter.lastBootId).isEqualTo("boot-test-001");
        assertThat(reporter.lastEventSeq).isEqualTo(3L);
    }

    @Test
    @DisplayName("升起态不允许再升（状态机拒绝）")
    void 升起态_拒绝再升() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForState(barrier, BarrierState.UP);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.OPEN, 2, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("升起");
    }

    @Test
    @DisplayName("升起态允许降下；降下态不允许再降")
    void 升降闭环_拒绝重复降() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForState(barrier, BarrierState.UP);
        barrier.execute(BarrierAction.CLOSE, 2, TRACE);
        waitForState(barrier, BarrierState.DOWN);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 3, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("降下");
    }

    @Test
    @DisplayName("动作进行中拒绝新指令（MOVING 态互斥）")
    void 动作中_拒绝新指令() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 2000); //长耗时保证处于 MOVING
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        assertThat(barrier.getState()).isEqualTo(BarrierState.MOVING);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 2, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("进行中");
    }

    @Test
    @DisplayName("seq 幂等：重复 seq 与乱序旧 seq 都被吸收，杆不会动两次")
    void seq幂等_重复与乱序被吸收() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        assertThat(barrier.execute(BarrierAction.OPEN, 5, TRACE)).isTrue();
        waitForState(barrier, BarrierState.UP);

        //平台超时重发同 seq=5：幂等忽略（返回 false），不触发任何动作与上报
        assertThat(barrier.execute(BarrierAction.OPEN, 5, TRACE)).isFalse();
        //乱序到达的旧 seq=3（即使是合法动作 CLOSE）：同样被吸收
        assertThat(barrier.execute(BarrierAction.CLOSE, 3, TRACE)).isFalse();
        assertThat(barrier.getState()).isEqualTo(BarrierState.UP);
        waitForExactly(reporter, BarrierState.MOVING, BarrierState.UP);

        //更大的新 seq 正常受理
        assertThat(barrier.execute(BarrierAction.CLOSE, 6, TRACE)).isTrue();
        waitForState(barrier, BarrierState.DOWN);
    }

    @Test
    @DisplayName("防砸互锁：杆下有车拒绝降杆，车走后恢复可降")
    void 防砸互锁_杆下有车拒绝降杆() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForState(barrier, BarrierState.UP);

        //杆下探测器报有车：降杆被安全互锁拒绝（压过一切指令来源）
        barrier.setVehiclePresent(true);
        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 2, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("防砸");
        assertThat(barrier.getState()).isEqualTo(BarrierState.UP);

        //车走了：恢复可降
        barrier.setVehiclePresent(false);
        assertThat(barrier.execute(BarrierAction.CLOSE, 3, TRACE)).isTrue();
        waitForState(barrier, BarrierState.DOWN);
    }

    @Test
    @DisplayName("卡杆故障：注入后动作执行到一半卡死转 FAULT 并上报")
    void 卡杆故障_动作中转FAULT() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 50);
        barrier.injectFault();

        assertThat(barrier.execute(BarrierAction.OPEN, 1, TRACE)).isTrue();
        waitForState(barrier, BarrierState.FAULT);

        //上报链：动作中 -> 故障（失败显式化，设备主动喊）——锁内置位与锁外上报间有间隙，轮询等齐
        waitForExactly(reporter, BarrierState.MOVING, BarrierState.FAULT);
    }

    @Test
    @DisplayName("故障态拒绝一切指令（停自动，等人工）")
    void 故障态_拒绝指令() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.injectFault();
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForState(barrier, BarrierState.FAULT);

        assertThatThrownBy(() -> barrier.execute(BarrierAction.OPEN, 2, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("故障");
        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 3, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("故障");
    }

    @Test
    @DisplayName("人工复位：故障清除，杆回落 DOWN 并上报；复位后接受新指令")
    void 人工复位_回落并恢复() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.injectFault();
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForState(barrier, BarrierState.FAULT);

        barrier.recover(TRACE);
        assertThat(barrier.getState()).isEqualTo(BarrierState.DOWN);
        assertThat(barrier.isFaultInjected()).isFalse();
        //复位回落也是一次状态变化：必须上报（平台台账随设备回正）——锁内置位与锁外上报有间隙，轮询等齐
        waitForExactly(reporter, BarrierState.MOVING, BarrierState.FAULT, BarrierState.DOWN);

        //复位后恢复正常接受指令
        assertThat(barrier.execute(BarrierAction.OPEN, 2, TRACE)).isTrue();
        waitForState(barrier, BarrierState.UP);
    }

    @Test
    @DisplayName("外力改态：物理世界掰杆，设备感知后主动上报；只支持稳定态")
    void 外力改态_主动上报() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);

        barrier.tamper(BarrierState.UP, TRACE);
        assertThat(barrier.getState()).isEqualTo(BarrierState.UP);
        assertThat(reporter.reported).containsExactly(BarrierState.UP);

        //外力只能掰到稳定态（MOVING/FAULT 是设备自己的执行态，不接受外部指定）
        assertThatThrownBy(() -> barrier.tamper(BarrierState.MOVING, TRACE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0.3静默故障：受理但无动作无上报（状态保持原样）——平台校正熔断的触发面")
    void 静默故障_受理不动作() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.setStuck(true);

        //受理成功（平台以为已送达）但杆无动作无上报，状态保持 DOWN
        assertThat(barrier.execute(BarrierAction.OPEN, 1, TRACE)).isTrue();
        assertThat(barrier.getState()).isEqualTo(BarrierState.DOWN);
        assertThat(reporter.reported).isEmpty();

        //恢复后正常动作
        barrier.setStuck(false);
        assertThat(barrier.execute(BarrierAction.OPEN, 2, TRACE)).isTrue();
        waitForState(barrier, BarrierState.UP);
    }

    @Test
    @DisplayName("0.4卡动作中：MOVING 后永不终态不上报（台账卡 MOVING——平台巡检告警的触发面）")
    void 卡动作中_不终态不上报() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        barrier.setStuckMoving(true);

        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        //等待：动作完成后仍卡 MOVING，且只上报过 MOVING（无 UP 终态）
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(barrier.getState()).isEqualTo(BarrierState.MOVING);
        assertThat(reporter.reported).containsExactly(BarrierState.MOVING);

        //recover 清除注入后可恢复
        barrier.recover(TRACE);
        assertThat(barrier.isStuckMovingInjected()).isFalse();
    }

    @Test
    @DisplayName("0.6双线seq：被拒过的指令重试说真话（不再被幂等线吞成假成功）")
    void 双线seq_被拒重试明确拒绝() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 30);
        //升起态下 CLOSE 被拒（防砸场景等价：先升到 UP 再注入车）
        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForState(barrier, BarrierState.UP);
        barrier.setVehiclePresent(true);

        //CLOSE seq=2 被防砸互锁拒绝
        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 2, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("防砸");
        //车走，受理 CLOSE seq=3（幂等线推进到 3）
        barrier.setVehiclePresent(false);
        assertThat(barrier.execute(BarrierAction.CLOSE, 3, TRACE)).isTrue();
        waitForState(barrier, BarrierState.DOWN);

        //平台重试被拒过的 seq=2：0.6 双线 -> 明确拒绝（而非 seq<=lastSeq(3) 被吞成"已执行"）
        assertThatThrownBy(() -> barrier.execute(BarrierAction.CLOSE, 2, TRACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("曾被设备拒绝");
        //从未见过的旧 seq=1 仍是幂等忽略（真重复）
        assertThat(barrier.execute(BarrierAction.OPEN, 1, TRACE)).isFalse();
    }

    @Test
    @DisplayName("traceId 贯穿（批次1）：指令链路号随动作事件上报透传（跨动作线程不丢）")
    void traceId随指令事件透传() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 10);

        barrier.execute(BarrierAction.OPEN, 1, TRACE);
        waitForExactly(reporter, BarrierState.MOVING, BarrierState.UP);

        //动作在独立线程执行（MDC 不自动传递）：traceId 靠显式参数透传，两次上报均同号
        assertThat(reporter.lastTraceId).isEqualTo(TRACE);
    }

    @Test
    @DisplayName("traceId 贯穿（批次1）：外力改态自发事件也携链路号")
    void traceId随外力改态透传() {
        FakeReporter reporter = new FakeReporter();
        Barrier barrier = newBarrier(reporter, 10);

        barrier.tamper(BarrierState.UP, TRACE);
        waitForExactly(reporter, BarrierState.UP);

        assertThat(reporter.lastTraceId).isEqualTo(TRACE);
        //外力改态非指令驱动：commandSeq 仍为 null（traceId 不改变协议语义）
        assertThat(reporter.commandSeqs).containsExactly((Long) null);
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

    /** 轮询至上报序列与期望一致（锁内置位与锁外上报有间隙；最多 1 秒） */
    private void waitForExactly(FakeReporter reporter, BarrierState... expected) {
        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            if (reporter.reported.size() >= expected.length
                    && reporter.reported.equals(java.util.Arrays.asList(expected))) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(reporter.reported).containsExactly(expected);
    }
}
