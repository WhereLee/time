package com.reason.modules.charging.enums;

import com.reason.common.exception.RRException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PileState 单元测试：迁移矩阵穷举 + 码映射
 *
 * <p>桩状态机：空闲 ↔ 充电中（充电开始/取消/结算切换），停用为终态（管理端维护入口，M1-8）。</p>
 */
@DisplayName("充电桩状态机")
class PileStateTest {

    @Test
    @DisplayName("合法迁移：空闲 → 充电中、充电中 → 空闲 不抛异常")
    void 合法迁移不抛() {
        assertTransitAllowed(PileState.IDLE, PileState.CHARGING);
        assertTransitAllowed(PileState.CHARGING, PileState.IDLE);
    }

    @Test
    @DisplayName("非法迁移：停用终态不可迁移；空闲不可跳停用（走管理端入口）")
    void 非法迁移抛业务异常() {
        //停用为终态（守卫语义：恢复走管理端人工流程，不提供状态机自动回迁）
        assertTransitRejected(PileState.DISABLED, PileState.IDLE);
        assertTransitRejected(PileState.DISABLED, PileState.CHARGING);
        //空闲/充电中不直接入停用——停用需管理端判定（占用中禁止停用），非会话状态机职责
        assertTransitRejected(PileState.IDLE, PileState.DISABLED);
        assertTransitRejected(PileState.CHARGING, PileState.DISABLED);
        //自迁移同样非法
        assertTransitRejected(PileState.IDLE, PileState.IDLE);
        assertTransitRejected(PileState.CHARGING, PileState.CHARGING);
        assertTransitRejected(PileState.DISABLED, PileState.DISABLED);
    }

    @Test
    @DisplayName("码映射：code ↔ 枚举一一对应")
    void 码映射正确() {
        assertThat(PileState.of(0)).isEqualTo(PileState.IDLE);
        assertThat(PileState.of(1)).isEqualTo(PileState.CHARGING);
        assertThat(PileState.of(2)).isEqualTo(PileState.DISABLED);

        assertThat(PileState.IDLE.getCode()).isEqualTo(0);
        assertThat(PileState.CHARGING.getCode()).isEqualTo(1);
        assertThat(PileState.DISABLED.getCode()).isEqualTo(2);
        assertThat(PileState.DISABLED.getDesc()).isNotBlank();
    }

    @Test
    @DisplayName("未知状态码：数据损坏保护，抛 IllegalArgumentException 而非静默")
    void 未知码抛异常() {
        assertThatThrownBy(() -> PileState.of(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知");
    }

    private void assertTransitAllowed(PileState from, PileState to) {
        assertThatCode(() -> PileState.assertCanTransit(from, to))
                .doesNotThrowAnyException();
    }

    private void assertTransitRejected(PileState from, PileState to) {
        assertThatThrownBy(() -> PileState.assertCanTransit(from, to))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("非法的充电桩状态迁移");
    }
}
