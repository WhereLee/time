package com.reason.modules.parking.enums;

import com.reason.common.exception.RRException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ParkSessionState 单元测试：迁移矩阵穷举 + 码映射
 *
 * <p>迁移矩阵即业务规则——合法/非法组合全部固化为断言，后续加状态时此测试强制同步矩阵。</p>
 */
@DisplayName("停车会话状态机")
class ParkSessionStateTest {

    @Test
    @DisplayName("合法迁移：进行中 → 已结束/已取消 不抛异常")
    void 合法迁移不抛() {
        assertTransitAllowed(ParkSessionState.ONGOING, ParkSessionState.FINISHED);
        assertTransitAllowed(ParkSessionState.ONGOING, ParkSessionState.CANCELLED);
    }

    @Test
    @DisplayName("非法迁移：终态不可逆（已结束/已取消不可再迁移）与自迁移均拒绝")
    void 非法迁移抛业务异常() {
        //终态 → 任何（含终态）均非法
        assertTransitRejected(ParkSessionState.FINISHED, ParkSessionState.CANCELLED);
        assertTransitRejected(ParkSessionState.FINISHED, ParkSessionState.ONGOING);
        assertTransitRejected(ParkSessionState.CANCELLED, ParkSessionState.ONGOING);
        assertTransitRejected(ParkSessionState.CANCELLED, ParkSessionState.FINISHED);
        //自迁移（无实际变更的调用）同样非法
        assertTransitRejected(ParkSessionState.ONGOING, ParkSessionState.ONGOING);
        assertTransitRejected(ParkSessionState.FINISHED, ParkSessionState.FINISHED);
        assertTransitRejected(ParkSessionState.CANCELLED, ParkSessionState.CANCELLED);
    }

    @Test
    @DisplayName("码映射：code ↔ 枚举一一对应")
    void 码映射正确() {
        assertThat(ParkSessionState.of(0)).isEqualTo(ParkSessionState.ONGOING);
        assertThat(ParkSessionState.of(1)).isEqualTo(ParkSessionState.FINISHED);
        assertThat(ParkSessionState.of(2)).isEqualTo(ParkSessionState.CANCELLED);

        assertThat(ParkSessionState.ONGOING.getCode()).isEqualTo(0);
        assertThat(ParkSessionState.FINISHED.getCode()).isEqualTo(1);
        assertThat(ParkSessionState.CANCELLED.getCode()).isEqualTo(2);
        assertThat(ParkSessionState.CANCELLED.getDesc()).isNotBlank();
    }

    @Test
    @DisplayName("未知状态码：数据损坏保护，抛 IllegalArgumentException 而非静默")
    void 未知码抛异常() {
        assertThatThrownBy(() -> ParkSessionState.of(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知");
    }

    private void assertTransitAllowed(ParkSessionState from, ParkSessionState to) {
        assertThatCode(() -> ParkSessionState.assertCanTransit(from, to))
                .doesNotThrowAnyException();
    }

    private void assertTransitRejected(ParkSessionState from, ParkSessionState to) {
        assertThatThrownBy(() -> ParkSessionState.assertCanTransit(from, to))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法的会话状态迁移");
    }
}
