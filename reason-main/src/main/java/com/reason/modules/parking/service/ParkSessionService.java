package com.reason.modules.parking.service;

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
}
