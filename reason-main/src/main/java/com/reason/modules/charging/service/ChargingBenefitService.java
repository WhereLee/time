package com.reason.modules.charging.service;

/**
 * 免停权益凭证能力（跨上下文出口：parking 出场核销走本接口，不直连 benefit_record 表）
 *
 * <p>凭证化边界（M1-1 定稿第 4 条）：签发在 charging 域（finish 事务内）；核销 = parking 在自身出场
 * 事务内调用本接口——REQUIRED 传播同库同事务，核销与停车结算要么都成要么都滚。按不可信对方设计：
 * check/redeem 入参只认权益码与会话 id，语义如同外部 API，进程边界不存在时由代码边界承载信任。</p>
 *
 * @date 2026-09-05
 */
public interface ChargingBenefitService {

    /**
     * 权益预检（只读，不改变权益状态）：按权益码查当前快照
     *
     * @param benefitNo 权益码
     * @return 权益视图；权益不存在返回 null（不可信输入：不存在≠异常，交给调用方分支决策）
     */
    BenefitView check(String benefitNo);

    /**
     * 凭证核销（最终判官）：条件更新 {@code 可用 → 已核销}，仅当权益码 + 状态可用 + 锚定会话一致同时命中
     *
     * <p>必须在调用方事务内执行（与出场结算同事务原子）。返回 false 意味着并发窗口内已被
     * 其他出场抢先核销（双花争夺败方）——调用方按"无减免结算"收尾并告警，不得重试覆盖。</p>
     *
     * @param benefitNo     权益码
     * @param parkSessionId 核销方停车会话 id（必须等于权益锚定会话，DB 级防错配）
     * @param parkOrderId   核销方停车订单 id（快照关联回写）
     * @param now           核销时间（秒）
     * @return true=核销成功；false=条件未命中（已核销/已过期/锚定错配/不存在）
     */
    boolean redeem(String benefitNo, Long parkSessionId, Long parkOrderId, long now);

    /**
     * 权益视图（跨上下文最小协议化数据，不暴露内部实体形态）
     *
     * @param benefitNo      权益码
     * @param freeSeconds    免停时长（秒）
     * @param expireTime     到期时间（秒）
     * @param state          状态：0-可用 1-已核销 2-已过期
     * @param anchorSessionId 锚定停车会话 id
     * @param sourceOrderId  来源充电订单 id
     */
    record BenefitView(String benefitNo, int freeSeconds, long expireTime, int state,
                       long anchorSessionId, long sourceOrderId) {
    }
}
