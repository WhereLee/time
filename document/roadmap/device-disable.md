# 待办：设备停用/退役机制（device disable）

> 状态：待触发
> 登记时间：2026-09-10（批次6 告警积压处置衍生——"演示设备休眠"的工程化替代）
> 触发条件：出现"设备档案长期不参与运行但不删除"的正式管理需求（真实设备退役、演示设备切换、形态共存）时

## 背景

云上告警积压处置时暴露：E-01/W-02 两台设备因"batch 形态不接入"需要长期静默，但系统没有"停用"语义，
只能徒手操作（`UPDATE device_record SET device_state=0` + remark 说明 + 历史告警批量确认）。

徒手操作依赖一个**隐式机制**且无落档入口：
- 升降杆样例的 **NOT_CONNECTED(0) 状态被三路口径天然排除**——scanOffline（`.ne(deviceState, NOT_CONNECTED)`）、
  BarrierAutoTask 同步排除、指标端点在线率口径一致；
- 配合 `DeviceMonitorServiceImpl.heartbeat()` 的 firstContact 豁免（心跳到达时该状态"只校正不告警"）——设备可被首次心跳自动复活。

问题：改库无审计；接手者不知道什么机制在起作用；误操作即触发误告警或意外复活。

## 方案（触发时执行）

1. `device_record` 增 `device_enabled tinyint(1) default 1`——停用是**档案属性**，不改状态机语义（与
   `document/knowledge/delete-strategy-soft-vs-disable.md` 的"禁用态"结论一致：删除本质是"不可用"且可逆 → 业务状态列）
2. 停用设备纳入"扫描/告警/指标/自动校正"四路统一排除口径（在现有 NOT_CONNECTED 三路之外补 enabled 过滤）
3. 管理端停用/启用入口：`@SysLog` 审计留痕 + 独立权限串；停用时的在途流水处置参照 FAULT 收口（PENDING → EXEC_FAILED，避免挂账到超时重试）
4. 启用后由心跳自然回归在线（复用 firstContact 语义，无需额外复活逻辑）

## 影响

- 本机制是未来"设备生命周期管理"（注册→服役→退役）的最小前身；
- 与删除策略的关系已有结论（禁用态优先，见 knowledge 文档），触发时直接沿用，不再重复决策。
