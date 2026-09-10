# 修复：台账序守卫的代际盲区——旧代际事件可重放

> 状态：已修（2026-09-11，批次8 独立质量评估发现）
> 来源：封版后代码通读——协议 v2 §3.3 序守卫只守"同代际单调"

## 缺陷描述

序守卫（`DeviceRecordServiceImpl.updateStateByEventWithSeq`）的接受条件是三取一：

1. 台账从未记录基线；2. `bootId` 不同（视为设备重启新代际）；3. 同代际且 `eventSeq` 更大。

第 2 条存在代际盲区：一份**被截获的旧代际事件**（或设备重启后才送达的迟到重投）重放时，
`bootId` 与当前台账不同 → 被当作"真重启"接受并重置基线。危害：

- 台账状态被回退到旧快照（如当前 UP 被旧 DOWN 事件改回 DOWN），要等下一次事件/心跳才自愈；
- 极端情况下若旧事件携带的 `commandSeq` 恰好命中一条在途 PENDING 流水，会被误销为 ARRIVED
  （概率低但路径存在）。

HMAC 只解决"伪造"，防重放由序守卫承担——而序守卫在跨代际这一步是敞开的。

## 修复

新增平台侧加固（线格式零变更，不触碰 DB 原子守卫）：

- `DeviceBootGenerationGuard`（config）维护每设备已见代际：
  - `barrier:boot-current:{deviceNo}` = 当前代际（快路径比对）；
  - `barrier:boot-history:{deviceNo}` SET = 已见代际集合（TTL 30 天）。
  - 判定：与当前代际一致 → 直通（稳态零额外历史查询）；换代际且历史命中 → 重放拒绝；
    换代际且历史未见 → 真重启，放行；Redis 异常 → fail-open + WARN（降级为修复前行为）。
- `DeviceEventServiceImpl.handleStateEvent` 在台账更新前调用 `isReplay`（命中则 warn 后 return，
  不推进台账/流水/告警）；台账接受后 `register` 登记代际。
- 进程启动 UUID（bootId）**不复用**是本守卫成立的前提——已在契约 §3.3 明示。

附带修复：设备重启后 broker 迟到的旧代际事件重投，此前会被当"新代际"接受（真 bug），
现在被正确拒绝。

## 边界（如实声明）

- Redis 数据丢失/回退后历史清空，守卫失效（与 seq 回退同一事故面，由启动对齐与
  对账升级告警兜底，不在此重复防护）；
- 多实例下两个新代际并发竞争窗口极小，退化行为=修复前现状（接受），无正确性危害增量。

## 回归验证

- `DeviceBootGenerationGuardTest` 8 例（稳态直通/首报/新代际/历史命中/Redis 异常 fail-open/
  register 幂等与 TTL/异常不抛）；
- `DeviceEventServiceImplTest` +2 例（已见代际重放拒绝且零副作用；接受后登记代际）。

## 关联

- contracts/PROTOCOL-V2.md §3.3（批次8 加固条款）
- document/plans/批次8-五项修复-方案.md 方案 3
