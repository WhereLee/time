# 升降杆设备协议 v2（PROTOCOL-V2）——一次定稿，双端契约

> 版本：v2（对应红队路线 0.1/0.5/0.7 的协议面定稿，见 document/plans/升降杆样例-红队评估与成长升级路线.md）
> 日期：2026-09-08
> 性质：**契约文档**——平台（reason-main）与设备（reason-barrier-sim）共同遵守；任一侧改动必须同步本文件与对侧实现，CI 双端构建钉住。
> 分层原则：**语义层**（报文结构/字段语义/签名载荷——与传输载体无关）与**传输适配层**（HTTP header vs MQ property）分离。本文定义语义层；当前传输为 HTTP 直连，MQ 迁移时仅动适配层。

## 1. 码值对照（双端枚举必须逐码一致）

| 语义 | 平台 DeviceState | sim BarrierState | 码值 |
|---|---|---|---|
| 升起（到位） | UP | UP | 1 |
| 降下（到位） | DOWN | DOWN | 2 |
| 动作中（非瞬时中间态） | MOVING | MOVING | 3 |
| 故障 | FAULT | FAULT | 4 |
| 未接入（仅平台台账建档态，设备永不报此码） | NOT_CONNECTED | — | 0 |

动作码（下行）：`OPEN`=升起、`CLOSE`=降下、`QUERY_STATE`=状态查询（v2 新增，非动作，不进指令流水）。

## 2. 下行契约（平台 → 设备，HTTP POST）

### 2.1 动作指令 POST /cmd（v1 保留，字段不变）
```json
{ "deviceNo": "BARRIER-E-01", "action": "OPEN", "commandSeq": 7 }
```
回执（HTTP 200 业务码；0.7 起协议错误改 4xx）：
```json
{ "code": 0, "msg": "指令已受理" }        // 受理（含 seq<=lastSeq 的幂等忽略——设备保证不重复动作）
{ "code": 1, "msg": "拒绝原因" }          // 拒绝：状态机不合法/防砸互锁/故障态/设备不存在/缺 seq
```
设备侧语义（v2 不变）：seq 幂等吸收重复/乱序；受理才推进 lastSeq；拒绝不推进。

### 2.2 状态查询 POST /cmd/query（v2 新增——T20 QUERY_STATE）
```json
{ "deviceNo": "BARRIER-E-01" }
```
应答（设备即时应答，不等动作）：
```json
{ "code": 0, "data": { "deviceNo": "BARRIER-E-01", "state": 1, "bootId": "a1b2c3...", "eventSeq": 42, "lastCommandSeq": 7 } }
{ "code": 1, "msg": "设备不存在" }
```
语义：平台主动询问设备实况（替代查自己写的台账快照）；用于超时对账、上行故障诊断。`lastCommandSeq`=设备最近受理的指令 seq（诊断用：判断平台在途指令是否已被更新指令覆盖）。**查询不是动作：不生成 commandSeq、不进 device_command_log、不触发 @ManualHold。**

## 3. 上行事件契约 v2（sim → 平台，POST /api/device/event）

### 3.1 报文（v1 → v2 字段变更）
```json
{
  "deviceNo": "BARRIER-E-01",
  "state": 1,
  "commandSeq": 7,        // v2 新增，可空：引起本次状态变化的指令 seq；外力改态/非指令驱动 = null
  "bootId": "a1b2c3d4e5", // v2 新增，必填：设备重启代际（进程启动 UUID）
  "eventSeq": 42          // v2 新增，必填：设备内单调递增的事件序号（重启后从 1 重新计，靠 bootId 区分代际）
}
```
- 签名域（0.5 启用，语义层现定）：HMAC-SHA256 覆盖规范化字段集 `deviceNo|state|commandSeq|bootId|eventSeq`，置传输头（HTTP `X-Device-Sign`，MQ 迁 message property）；验签先于 XssFilter。
- 兼容策略：**双端同仓同步发布，v2 字段必填，无 v1 兼容层**（样例双端同发；若将来出现存量旧设备，另行评估网关翻译层）。

### 3.2 事件-平台动作映射（v2 语义——销账必须证据驱动）
| 事件 | commandSeq | 平台动作 |
|---|---|---|
| state=UP(1) | 非空 | 台账更新（带序守卫）+ **按 (deviceNo, seq) 精确销账**该 OPEN 流水（seq 匹配 + action 匹配 + PENDING 才销）→ ARRIVED |
| state=UP(1) | 空 | 台账更新（带序守卫）；**不动流水**（外力改态——无指令可销，不再"按动作猜"） |
| state=DOWN(2) | 非空 | 台账更新 + 按 seq 精确销账 CLOSE 流水 |
| state=DOWN(2) | 空 | 台账更新；不动流水 |
| state=FAULT(4) | 任意 | 台账更新 + 在途 PENDING 全部置 EXEC_FAILED + DEVICE_FAULT 告警（0.6 细化为按 seq 归属中断） |
| state=MOVING(3) | 任意 | 台账更新（带序守卫）；流水不动（继续等到位） |

### 3.3 台账序守卫（防重放/乱序/陈旧覆盖）
- 台账记录 `last(bootId, eventSeq)`（device_record 新增两列）。
- 同 bootId：`eventSeq <= lastEventSeq` → 重放/乱序，**拒绝**（400 级响应，记 warn）。
- bootId 变化：设备重启代际切换 → **接受并重置基线**（重启自述/重启后首批事件不被旧序误拒；0.5 起此接受须叠加身份验证）。
- 心跳通道（v1 语义，0.5 起同权签名）：仍走"grace 让路 + 稳定态校正"逻辑，不参与事件序守卫——心跳是自述对账信号，不是事件流。

## 4. 上行心跳契约（v1 保留，字段不变）
```json
{ "deviceNo": "BARRIER-E-01", "state": 2 }
```
- 平台语义：在线 TTL 续命 + 状态对账（仅稳定态 UP/DOWN/FAULT；首次接入豁免；grace 让路窗口参数化按注入器剧本标定）。
- 0.7 起：state 允许 null（只报活不报态），非法码返回 400 语义化错误且不影响在线 key 刷新。

## 5. 错误语义约定（0.7 目标，先定稿）
| 场景 | HTTP | body code | 说明 |
|---|---|---|---|
| 业务拒绝（设备侧：动作不合法/防砸/故障） | 200 | 1 | 业务码模式（sim 回执） |
| 平台业务拒绝（令牌无效/设备未登记/状态码非法） | **401/400** | 500/自定义 | 0.7 起平台不再 200+code500 |
| 协议垃圾（字段类型错/缺必填） | **400** | 自定义 | 0.7 起 sim 不再 ClassCastException 500 |
| 链路故障（连接失败/超时） | — | — | 平台侧按 4xx/5xx/连接异常细分 SEND_FAILED 原因（离线/拒绝/协议错） |

## 6. 启用步序（实现分步，契约一次定稿）
| 步 | 范围 | 内容 |
|---|---|---|
| 0.1 | 本契约 2.2/3.1/3.2/3.3 | 事件 v2 字段 + 序守卫 + 按 seq 销账 + QUERY_STATE + sim 上报失败重试（重试靠 eventSeq 幂等） |
| 0.5 | 3.1 签名域 | HMAC/nonce/per-device secret + 下行凭证 + 管理面分离 |
| 0.6 | 3.2 FAULT 行细化 | FAULT 按 seq 归属中断在途 + sim 双线 seq（已执行/已拒绝） |
| 0.7 | §5 | 错误语义对称化 + state 可空 |
| 阶段2 | 传输适配层（§7） | 事件走 MQ 时：3.1 报文原样入消息体，签名置 message property（双写期过渡，D6） |

## 7. MQ 传输适配层（阶段2 定稿——事件走 RocketMQ，与 HTTP 双写并行）

> 语义层（§1/§3）不变；本节只定义传输适配（分层原则：换载体不换语义）。心跳不走 MQ（D3 定稿：判活要有界时延，broker 故障时心跳+自述对账仍活）。下行指令仍 HTTP（QUERY_STATE 依赖同步请求应答）。

### 7.1 消息信封
- **topic**：`device-event`（单读写队列 writeQueueNums=readQueueNums=1——全局有序，D5）；**消费组**：`platform-device-event`（broker 侧 retryMaxTimes=3，耗尽进死信 `%DLQ%platform-device-event`）。
- **消息体**：§3.1 报文原样 JSON（deviceNo/state/commandSeq/bootId/eventSeq，与 HTTP body 完全同构，commandSeq 可空）。
- **message property**：`X-Device-No`（设备号）、`X-Device-Sign`（签名，canonical 与 §3.1 同一拼法 `deviceNo|state|commandSeq|bootId|eventSeq`，HMAC-SHA256 per-device secret）。
- **keys**：`{deviceNo}-{eventSeq}`（broker 侧排查锚点，不参与业务）。
- **信封完整性**：property `X-Device-No` 必须与消息体 `deviceNo` 一致（不一致=信封被拼改，毒消息）。

### 7.2 保序约束（D5，双端铁律）
- sim 侧：单发送线程 + 有界队列按序 drain；**发送失败的事件队首持留**（不让位重排——失败事件若让位，后续更大 eventSeq 先到平台，本事件恢复后反被 §3.3 序守卫当旧序拒=真丢数据）；退避 1s/2s/3s/4s/5s→超限转 5s 节拍等 broker 恢复；队列满（默认 500）丢弃最旧记 error（平台 QUERY_STATE/对账兜底）。
- 平台侧：消费并发度=1（单消费线程 receive(1)→处理→ack）；不引入任何并发消费。

### 7.3 消费语义与失败分级（协议 §5 错误语义在 MQ 侧的映射）
| 场景 | MQ 侧处置 | 对应 HTTP 语义 |
|---|---|---|
| 验签失败/未登记设备/协议垃圾/信封不一致 | **ack 丢弃 + error 日志**（毒消息不入重投循环） | 401/400 |
| 业务拒绝（RRException，如未登记） | ack 丢弃 + error 日志 | 400 |
| 业务瞬时异常（DB 抖动等） | **不 ack**，broker 重投（retryMaxTimes=3）→ 耗尽进死信 | 5xx 重试 |
| 重复投递 | 业务幂等吸收（§3.3 序守卫 + 按 seq 精确销账），consumer 不做额外去重 | — |
| 平台停机窗口 | 未 ack 消息由 broker 持久化，重启后续消费（零丢失来源） | — |

### 7.4 双写期（D6，本阶段终态）
- sim 事件同时发 HTTP（§3，行为与阶段1 完全一致）与 MQ（本节）；平台两路径并行处理同事件，由业务幂等吸收（后到路径被序守卫拒=正常对照痕迹）。
- HTTP 通道退役判定在阶段 3 量下回归通过后，另立契约修订；`sim.mq.enabled`/`reason.device.mq.enabled` 任一关闭即退化纯 HTTP 形态（回滚开关）。
