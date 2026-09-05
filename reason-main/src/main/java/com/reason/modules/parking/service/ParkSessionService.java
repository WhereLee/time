package com.reason.modules.parking.service;

import com.reason.common.utils.PageUtils;
import com.reason.modules.parking.form.ParkSessionForm;

/**
 * 停车会话服务（状态机：进行中 → 已结束 | 已取消，终态不可逆）
 *
 * <p>并发正确性：占用/释放车位一律走条件更新（原子判定+写入），
 * 会话状态终态化同样条件更新——详见实现注释。</p>
 *
 * @date 2026-09-04
 */
public interface ParkSessionService {

    /**
     * 入场：设备上报（车位编号 + 车牌）→ 占用车位并创建进行中会话
     *
     * @param spaceNo 车位编号（不区分大小写，入库统一大写）
     * @param plateNo 车牌号（trim + 统一大写）
     * @return 会话 id
     * @throws com.reason.common.exception.RRException 车位不存在/已禁用/已被占用（含并发被占）
     */
    Long entry(String spaceNo, String plateNo);

    /**
     * 取消：进行中会话可取消（终态不可逆），车位释放
     *
     * @param sessionId    会话 id
     * @param cancelReason 取消原因（可选）
     * @throws com.reason.common.exception.RRException 会话不存在/非进行中（非法迁移）/状态已被并发变更
     */
    void cancel(Long sessionId, String cancelReason);

    /**
     * 出场结算：会话终态化 + 算费生成订单快照 + 释放车位（单事务，判官前置）
     *
     * @param sessionId 会话 id
     * @return 订单 id
     * @throws com.reason.common.exception.RRException 会话不存在/非进行中（非法迁移）/无启用规则/状态已被并发变更/车位状态异常
     */
    Long exit(Long sessionId);

    /**
     * 查询指定车位的进行中停车会话（跨上下文只读能力：charging 域充电开始锚定用）
     *
     * <p>返回轻量视图而非实体：跨限界上下文只传递协议化数据，不暴露内部实体形态
     * （凭证化边界的另一面——出方向也只给最小必要信息）。</p>
     *
     * @param spaceId 车位 id
     * @return 进行中会话视图；车位空闲/无会话时返回 null
     */
    OngoingParkSession getOngoingBySpaceId(Long spaceId);

    /**
     * 进行中停车会话视图（会话 id + 车牌 + 车位编号；车牌供充电开始的一致性校验）
     */
    record OngoingParkSession(Long sessionId, String plateNo, String spaceNo) {
    }

    /**
     * 会话分页查询（管理端只读：车牌/车位模糊 + 状态筛选）
     */
    PageUtils queryPage(ParkSessionForm form);
}
