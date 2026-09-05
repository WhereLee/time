package com.reason.modules.charging.enums;

import com.reason.common.exception.RRException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChargeSessionState 单元测试：迁移矩阵穷举 + 码映射
 *
 * <p>迁移矩阵即业务规则——合法/非法组合全部固化为断言，后续加状态时此测试强制同步矩阵。</p>
 */
@DisplayName("充电会话状态机")
class ChargeSessionStateTest {

    @Test
    @DisplayName("合法迁移：充电中 → 已结束/已取消/超时结束 不抛异常")
    void 合法迁移不抛() {
        assertTransitAllowed(ChargeSessionState.CHARGING, ChargeSessionState.FINISHED);
        assertTransitAllowed(ChargeSessionState.CHARGING, ChargeSessionState.CANCELLED);
        assertTransitAllowed(ChargeSessionState.CHARGING, ChargeSessionState.TIMEOUT_FINISHED);
    }

    @Test
    @DisplayName("非法迁移：终态不可逆（已结束/已取消/超时结束不可再迁移）与自迁移均拒绝")
    void 非法迁移抛业务异常() {
        //终态 → 任何状态均非法
        assertTransitRejected(ChargeSessionState.FINISHED, ChargeSessionState.CANCELLED);
        assertTransitRejected(ChargeSessionState.FINISHED, ChargeSessionState.TIMEOUT_FINISHED);
        assertTransitRejected(ChargeSessionState.CANCELLED, ChargeSessionState.CHARGING);
        assertTransitRejected(ChargeSessionState.TIMEOUT_FINISHED, ChargeSessionState.CHARGING);
        //自迁移（无实际变更的调用）同样非法
        assertTransitRejected(ChargeSessionState.CHARGING, ChargeSessionState.CHARGING);
        assertTransitRejected(ChargeSessionState.FINISHED, ChargeSessionState.FINISHED);
        assertTransitRejected(ChargeSessionState.CANCELLED, ChargeSessionState.CANCELLED);
        assertTransitRejected(ChargeSessionState.TIMEOUT_FINISHED, ChargeSessionState.TIMEOUT_FINISHED);
    }

    @Test
    @DisplayName("码映射：code ↔ 枚举一一对应")
    void 码映射正确() {
        assertThat(ChargeSessionState.of(0)).isEqualTo(ChargeSessionState.CHARGING);
        assertThat(ChargeSessionState.of(1)).isEqualTo(ChargeSessionState.FINISHED);
        assertThat(ChargeSessionState.of(2)).isEqualTo(ChargeSessionState.CANCELLED);
        assertThat(ChargeSessionState.of(3)).isEqualTo(ChargeSessionState.TIMEOUT_FINISHED);

        assertThat(ChargeSessionState.CHARGING.getCode()).isEqualTo(0);
        assertThat(ChargeSessionState.FINISHED.getCode()).isEqualTo(1);
        assertThat(ChargeSessionState.CANCELLED.getCode()).isEqualTo(2);
        assertThat(ChargeSessionState.TIMEOUT_FINISHED.getCode()).isEqualTo(3);
        assertThat(ChargeSessionState.TIMEOUT_FINISHED.getDesc()).isNotBlank();
    }

    @Test
    @DisplayName("未知状态码：数据损坏保护，抛 IllegalArgumentException 而非静默")
    void 未知码抛异常() {
        assertThatThrownBy(() -> ChargeSessionState.of(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知");
    }

    private void assertTransitAllowed(ChargeSessionState from, ChargeSessionState to) {
        assertThatCode(() -> ChargeSessionState.assertCanTransit(from, to))
                .doesNotThrowAnyException();
    }

    private void assertTransitRejected(ChargeSessionState from, ChargeSessionState to) {
        assertThatThrownBy(() -> ChargeSessionState.assertCanTransit(from, to))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法的充电会话状态迁移");
    }
}
