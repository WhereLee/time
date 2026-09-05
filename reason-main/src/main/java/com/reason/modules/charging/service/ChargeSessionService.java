package com.reason.modules.charging.service;

/**
 * 充电会话服务（状态机：充电中 → 已结束 | 已取消 | 超时结束，终态不可逆）
 *
 * <p>锚定链：桩 → 绑定位（charging_pile.space_id）→ 该车位进行中停车会话（parking 跨上下文只读能力）。
 * 充电必须发生在停车中：无进行中停车会话拒绝开始、车牌不一致拒绝开始（防凭证错配）。</p>
 *
 * <p>并发正确性：充电开始 = 桩状态条件更新（空闲→充电中，原子判定+写入）为最终判官，
 * 与停车入场判官同模式——并发重复上报仅一方行数 &gt; 0。</p>
 *
 * @date 2026-09-05
 */
public interface ChargeSessionService {

    /**
     * 开始充电：设备上报（桩编号 + 车牌）→ 锚定该车位进行中停车会话并创建充电中会话
     *
     * @param pileNo  桩编号（不区分大小写，入库统一大写）
     * @param plateNo 车牌号（trim + 统一大写，须与锚定停车会话车牌一致）
     * @return 充电会话 id
     * @throws com.reason.common.exception.RRException 桩不存在/已停用/无进行中停车会话（无停车不充电）
     *                                                /车牌与停车会话不一致/桩已被并发占用（重复开始）
     */
    Long start(String pileNo, String plateNo);

    /**
     * 取消充电：充电中会话可取消（终态不可逆），桩释放回空闲
     *
     * <p>取消仅终止充电，不影响锚定的停车会话（车仍在车位）。</p>
     *
     * @param sessionId    充电会话 id
     * @param cancelReason 取消原因（可选）
     * @throws com.reason.common.exception.RRException 会话不存在/非充电中（非法迁移）/状态已被并发变更/桩状态异常（回滚暴露）
     */
    void cancel(Long sessionId, String cancelReason);
}
