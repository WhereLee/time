# 阶段 2：RocketMQ 可靠事件通道——需求规格（供外部开发 agent 执行）

> 出处：document/plans/升降杆样例-红队评估与成长升级路线.md §6 阶段 2（决策门）+ §8 决策点 D3/D5/D6/D7
> 关联契约：contracts/PROTOCOL-V2.md（协议 v2——事件载荷/签名规范已含 MQ 迁移说明）
> 拍板记录：用户 2026-09-08 指令"给出阶段二详细需求并交由 agent 开发"= **go/no-go 判 GO**（理由：审计完整性目标进入目标集——MQ 独有增量①流水"真实到位时刻"可精确回答 ②平台重启窗口内动作零补证往返 ③事件通道可靠性不再依赖 HTTP 重试窗口；量级预算不构成理由，见红队文档）
> 本规格面向无项目上下文的开发 agent：**所有决策已定稿，禁止自行新开决策；遇到规格未覆盖处先停手，按 §9 联系方式标注为阻塞项**
> 日期：2026-09-08

---

## 0. 阅读顺序（必读）

1. 本文档（规格）——照此实现
2. `contracts/PROTOCOL-V2.md`——事件载荷/签名的语义定义（实现前通读，改动契约必须双端同步）
3. 红队文档 §6 阶段 2 小节（目标取舍/理由链/边界）——理解"为什么"，不引入新需求
4. 代码基线清单（§8）——照清单定位复用点，**优先复用，禁止平行重写**

---

## 1. 背景与现状基线（已实证，勿破坏）

- 设备上报走 HTTP：sim → 平台 `/api/device/event`（事件）+ `/api/device/heartbeat`（心跳，10s 周期，TTL 30s 判活）
- 事件协议 v2（0.1）：事件体 `{deviceNo, state, commandSeq(可空), bootId, eventSeq}`；签名（0.5）：HMAC-SHA256，canonical=`deviceNo|state|commandSeq|bootId|eventSeq`，HTTP 放 header `X-Device-No`+`X-Device-Sign`；契约文档写明：**MQ 迁移时签名进 message property（同一 canonical）**
- 平台处理入口（HTTP）：`DeviceEventController` → `DeviceChannelAuthenticator.authenticateEvent(...)`（验签，纯参数方法，与通道无关）→ `DeviceEventServiceImpl.handleStateEvent(DeviceEventForm)`（编排：台账序守卫更新/按 seq 销账/FAULT 中断/告警——全部复用，消费路径不得绕开）
- 幂等已具备：台账 `updateStateByEventWithSeq` 按 (bootId, eventSeq) 条件更新拒重放/乱序；流水按 (deviceNo, seq, action) 精确销账——**业务层消费幂等天然成立，MQ 重复投递会被业务幂等吞掉（验收点）**
- 心跳不走 MQ（决策 D3 定稿）：心跳留 HTTP——判活不依赖 broker；broker 故障时 liveness+自述对账仍活
- 双实例已实证（阶段 1）：Quartz 集群互斥/接管 OK；**本阶段 MQ 消费拓扑为单队列保序，MQ 消费组多实例负载均衡不在本阶段**（单队列下无意义，阶段 3 上量时扩队列+每设备 message group 再议）
- sim 事件上报已异步化（0.1）：单发送线程保序（乱序会被平台序守卫当旧序拒=丢数据，D5 约束在此）

## 2. 目标与范围

### 目标
事件可靠投递升级：sim 事件经 RocketMQ 投递，平台重启窗口内不丢账、流水"真实到位时刻"可精确回答（事件即达即消费，无需心跳补证往返）；与 HTTP 双写期共存，不破坏已实证机制。

### 范围（做）
| # | 项 | 落点 |
|---|---|---|
| A | 依赖与本地 broker 探测/接线（含 topic 创建） | 双端 pom + 本机 broker |
| B | sim 侧 MQ 事件发送器（producer）：双写（HTTP 保留 + MQ 新增）、broker 故障缓冲重试不丢、message property 签名 | reason-barrier-sim |
| C | 平台侧 MQ 事件消费者（consumer）：验签（复用 DeviceChannelAuthenticator）、编排复用（DeviceEventServiceImpl）、消费失败分级（毒消息丢弃/瞬时故障重投）、单并发保序 | reason-main |
| D | 双写期一致性留痕（消费日志含双路径对照信息） | reason-main consumer 日志 |
| E | 契约文档同步：PROTOCOL-V2.md 增补 MQ 传输适配小节（topic/消息体/message property/消费语义/重试与死信语义） | contracts/ |
| F | 测试：单测（信封构造/验签消费编排，注入回调不连 broker）+ MQ 端到端 IT（Testcontainers rocketmq，标 @Tag("mq")，独立 CI job） | 双端 test |
| G | 配置项与开关（双写开关默认开；topic/消费组/地址可配） | 双端 yml |

### 范围外（不做，显式）
- ❌ 心跳走 MQ（D3 定稿：留 HTTP）
- ❌ HTTP 事件通道退役（D6 双写期为本阶段终态；退役判定在阶段 3 回归通过后，另立任务）
- ❌ 消费组多实例负载均衡/扩队列（阶段 3 上量主题）
- ❌ broker 生产部署/运维（本机 5.3.1 为开发联调形态；broker ACL/TLS 不因同机豁免——接入代码预留 property 级鉴权扩展点即可，不实现）
- ❌ 下行指令走 MQ（平台→sim 指令仍 HTTP——T20 QUERY_STATE 等依赖同步请求应答，无异步化需求）
- ❌ 事件时间基准重构（D7 定稿：平台写账时间=处理时刻，事件内设备采样时刻不引入——现协议无该字段，不加）
- ❌ 改动协议 v2 语义层（commandSeq/bootId/eventSeq 语义、序守卫、canonical 拼法一律不动）

## 3. 架构决策（全部定稿，出处见红队文档 §8）

| 决策 | 定稿 | 理由（简） |
|---|---|---|
| D3 心跳与事件是否同走 MQ | **事件走 MQ、心跳留 HTTP** | 心跳要"有界时延"判活，MQ 提供持久可靠但时延方差为代价；心跳留 HTTP 提供故障域隔离（broker 故障时判活+自述仍活） |
| D5 消费有序性 | **单队列全局有序 + 平台消费并发度=1** | 平台序守卫把乱序迟到真事件当旧序拒=丢数据；本地样例量级下单队列绝对保序且零 FIFO 配置负担 |
| D6 退役时机 | **双写期（HTTP+MQ 并行）为本阶段终态**；平台以 MQ 为准体现在：退役判定（阶段 3）只看 MQ 覆盖 | 无双写期则 MQ 侧事故只能整体回滚 |
| D7 事件时间基准 | 处理时刻为准（现状不引入设备采样时刻字段） | 单一时钟源铁律延续 |
| 客户端选型 | **已定稿（探测完成 2026-09-08）：`rocketmq-client-java` 5.3.x**（gRPC 协议）——本机 broker 5.3.1 的 proxy gRPC 8081 已实测可用（namesrv 9876 / broker 10911 / proxy 8081 三端口全通） | 不引 rocketmq-spring-boot-starter：其 2.3.x 止步 4.x remoting 模型，5.x 演进中被官方边缘化 |

## 4. 需求分解（实现顺序 A→G；每子步可独立提交）

### A. 环境与依赖

**A1** reason-main `pom.xml`：按 §3 探测结果加客户端依赖（版本号写定，禁止 LATEST）。
**A2** reason-barrier-sim `pom.xml`：同上（producer 侧）。
**A3（已由主 agent 于 2026-09-08 探测完成，直接采用结论）** broker 环境：本机 RocketMQ 5.3.1（`ROCKETMQ_HOME=D:\rocketmq-5.3.1\rocketmq-all-5.3.1-bin-release`），**gRPC 8081 可用（proxy cluster 模式）** → 客户端定稿 `org.apache.rocketmq:rocketmq-client-java:5.3.x`（gRPC 端点 `127.0.0.1:8081`）。本机三进程启动：双击 `D:\rocketmq-5.3.1\start-rocketmq.bat`（namesrv+broker+proxy 三个窗口）；proxy 配置 `conf/proxy-dev.json`（JSON 格式——注意 5.3.1 mqproxy 参数为 `-pm cluster -n 127.0.0.1:9876 -pc <json>`，非 -c；配置文件是 JSON 非 properties，踩坑记录见 D:\rocketmq-5.3.1 目录注释）。若代理窗口失败，remoting 10911 仍可用（降级分支见 §3）。
**A4** topic 创建：`device-event` 单读写队列（writeQueueN=1, readQueueN=1），消费组 `platform-device-event`。创建命令与结果记入 MQ-ENV-NOTES.md。

### B. sim 侧 MQ 事件发送器（reason-barrier-sim）

**B1 新类 `MqEventReporter`**（建议包 `com.reason.barrier.reporter`，实现现 `EventReporter` 接口——先读接口与 HttpEventReporter 再动）：
- 发送语义：与 HttpEventReporter 同构的异步单发送线程（**保序铁律**——乱序会被平台序守卫拒）；消息体 = 协议 v2 事件 JSON（与现 HTTP body 完全同构：deviceNo/state/commandSeq/bootId/eventSeq，HashMap 允许 null）
- 签名：canonical 不变（DeviceSignature.canonicalEvent），签名与设备号放 **message property**：`X-Device-No`、`X-Device-Sign`（契约 §3.1 已定：MQ 迁移签名进 message property）
- 失败缓冲重试（验收硬指标"broker 故障上报不丢"）：发送失败（broker 不可达/网络异常）→ 内存有界队列（容量 500，满则记 error 丢弃最旧——平台 QUERY_STATE 兜底，注释注明）→ 退避重试 1s/2s/3s…上限 5 次/条 → 仍失败保留在队列尾部继续等 broker 恢复（队列内顺序不得乱）→ 恢复后按序补发
- **broker 不可用不得阻塞动作线程**（沿用异步模型）；producer 启动失败不炸应用（记 error，后续发送重试时懒重建 producer——或等效方案，注释说明取舍）
- 双写：`EventReporter` 现有调用点（Barrier 动作完成后）保持调用；配置 `sim.mq.enabled` 控制 MQ 发送开关（默认 true），HTTP 发送保留不删（D6 双写期）

**B2** 配置（sim `application.yml`）：`sim.mq.nameserver/gRPC endpoint`（依 A3 分支）、topic `device-event`、enabled、队列容量。**B3** 生命周期：@PreDestroy 优雅关闭（队列剩余尽力发或记日志，取舍注释）。

### C. 平台侧 MQ 事件消费者（reason-main）

**C1 新组件（建议 `com.reason.modules.device.mq` 包）**：消费者（依 A3 分支用 PushConsumer 或 SimpleConsumer）：
- 订阅 `device-event`，消费组 `platform-device-event`，**并发度=1（保序，注释写明 D5 约束）**
- 消费处理：取 message property `X-Device-No`/`X-Device-Sign` + 消息体事件字段 → **先 `DeviceChannelAuthenticator.authenticateEvent(...)` 验签（直接复用现组件，勿重写）** → 验签通过后构造 `DeviceEventForm` → **`DeviceEventServiceImpl.handleStateEvent(form)`（直接复用现编排，勿重写）**
- **消费失败分级**：
  - 验签失败/未登记设备/协议垃圾（平台侧判定 4xx 语义）→ **ack 丢弃 + error 日志**（注明"毒消息不入重投循环"，防无限重投刷 broker）
  - 业务处理抛 DB 瞬时异常 → 不 ack（触发 MQ 重投，消费组重试 3 次后进死信队列/放弃——按所选客户端语义，行为与日志写明）
- **幂等**：重复投递天然被业务幂等吞掉（序守卫）——consumer 不做额外去重（注释说明，验收验证）
- 日志（D6 留痕）：消费成功记 debug/info：`[MQ事件消费] deviceNo=... eventSeq=... bootId=... commandSeq=... state=...（双写期：HTTP 路径并行处理同事件由业务幂等吸收）`
- 异常保护：consumer 线程内任何意外异常不得打爆进程（catch 最外层按分级规则处理）

**C2** 配置（main `application.yml` + `application-dev.yml`，不进 prod 明文）：`reason.device.mq.*`（endpoint/nameserver、topic、消费组、enabled 默认 true——enabled=false 时 consumer 不启动，回滚开关）。

### D. 双写期一致性（不新增表）

以消费日志对照为最小实现（见 C1 日志）；不建对照表、不做强校验（本阶段退役判定不依赖它）。规格裁剪说明写入交付物。

### E. 契约文档同步（contracts/PROTOCOL-V2.md）

在"传输适配层"相关章节新增小节（或修订 3.1 签名说明，若已含 MQ 迁移注记则补全细节）：
- MQ 消息信封：topic=device-event；消息体=事件 JSON（同 HTTP body）；message property：`X-Device-No`/`X-Device-Sign`
- 消费语义：消费组 platform-device-event，单队列有序，并发度 1；重复投递由平台业务幂等吸收（序守卫）
- 失败语义：平台 4xx 类拒绝（验签失败等）= ack 丢弃记日志；瞬时故障=MQ 重投（3 次）→ 死信
- 心跳：仍走 HTTP（D3），MQ 不承载
- 双端（平台/契约）同步改；**sim 侧代码改动同时提交**（0.0 纪律：契约改动双端锁步，CI 双 job 钉住）

### F. 测试

**F1 单测（不连 broker）**：
- sim：MqEventReporter 信封构造（canonical 与 property 一致性：给定事件字段，X-Device-Sign == DeviceSignature.sign(secret, canonicalEvent(...))）——用注入 fake 客户端断言发送的消息体/property
- main：消费编排（构造消息对象 → 消费处理函数 → verify authenticateEvent + handleStateEvent 被调）；分级规则（验签失败→ack 丢弃路径；业务异常→重投路径）
**F2 MQ 端到端 IT（连 broker）**：新测试类标 `@Tag("mq")`（参考现有 IT 的 Testcontainers 用法——查 reason-main 测试目录既有容器测试的写法照抄结构）：
- RocketMQ 容器：Testcontainers `GenericContainer` 链 namesrv + broker（镜像 apache/rocketmq:5.3.x，参考社区标准启动命令；冷启动慢→容器单例/复用，超时给足）
- 用例：①发送→消费→台账/流水终态正确（真事件闭环）②重复投递（同 eventSeq 双发）→ 业务幂等吞掉（流水无重复/台账一致）③重启续消费（可降级为"消费积压后恢复消费不丢"若容器重启成本过高——**取舍须在交付物写明**）④验签失败消息被丢弃不重投（broker 侧消息数/消费日志断言）
- **CI 策略（红队文档 §6 三选一，已定）**：`@Tag("mq")` IT 放 CI 独立 job（`.github/workflows/ci.yml` 新增或扩展现有 job），跑容器；**若实测 CI 容器不稳（>2 次红），显式声明降级：MQ 端到端仅本地手动验证 + IT 类留本地跑，CI 只编译+单测——降级决定与证据写入交付物，不许沉默跳过（T18 教训）**
**F3 本地运行验证剧本**（交付物含脚本或文档化命令）：
- 双写期正常链路：指令→动作→事件 HTTP+MQ 双达→平台销账一次（流水 ARRIVED 唯一）
- broker 停 30s：sim 队列积压、平台心跳判活不受影响（设备不误 OFFLINE——D3 收益实证）
- broker 恢复：积压事件按序补发→平台消费→台账/流水终态正确、无重复销账
- 平台重启窗口：平台停 60s 期间设备动作 N 起 → 重启后（MQ 消息在 broker 持久化）全部消费、流水终态一致

### G. 配置项清单（落地 yml 时对照）

| 项 | 默认 | 说明 |
|---|---|---|
| main: reason.device.mq.enabled | true | 关闭即回滚到纯 HTTP（consumer 不启动） |
| main: reason.device.mq.endpoint / nameserver | 依 A3 | 客户端地址 |
| main: reason.device.mq.topic | device-event | |
| main: reason.device.mq.consumer-group | platform-device-event | |
| sim: sim.mq.enabled | true | 双写开关 |
| sim: sim.mq.endpoint / nameserver | 依 A3 | |
| sim: sim.mq.topic | device-event | 与平台一致 |
| sim: sim.mq.buffer-size | 500 | 失败缓冲队列上限 |

## 5. 编码规范与工程约束（外部 agent 必须遵守）

1. 中文注释，风格与邻接代码一致（意图注释，不写废话）；禁止删改既有注释
2. 复用优先：DeviceChannelAuthenticator/DeviceEventService/EventReporter 接口/DeviceSignature 一律复用；发现需小改复用组件时，改 + 补单测 + 注释标明原因，禁止绕开重写
3. 异常处理：平台 4xx 语义拒绝 vs 瞬时故障重投的分级必须显式（见 C1），不许 catch-all 吞异常
4. 线程：sim 发送线程保序（单线程队列）；平台消费并发度=1；线程命名带业务前缀（如 barrier-mq-producer/consumer）
5. 测试：JUnit5 + AssertJ + Mockito（照现测试风格）；新逻辑必须有单测；CI 必须绿（含 sim job）
6. commit 规范：前缀 feat:/fix:/test:/docs:/chore:，中文描述，**每子步（A→G 或更细）独立 commit，不得攒批**；本地全绿后才 push（中间态不推——攒批红 CI 教训）
7. 禁简化红线：不得以"样例规模小"为由砍需求（§2 范围已定，不做=范围外清单所列）；规格未覆盖=停手记阻塞，不许自由发挥
8. 密钥/凭据零明文入库（本阶段无新增密钥；broker 无密码本地联调，代码不留假凭据占位）

## 6. 完成定义（DoD，全部满足才算完成）

- [ ] A3 探测记录（MQ-ENV-NOTES.md）含选型结论；topic/消费组创建成功
- [ ] B/C 代码落地，复用点零重写（diff 可查），双写期 HTTP 路径行为不变（回归：现有端到端剧本仍绿）
- [ ] F1 单测全绿（平台+sim）；F2 MQ IT 本地全绿；CI 全绿（含 mq job 或显式降级声明）
- [ ] F3 剧本逐条跑通，证据记录（时间/输出/结论）写入交付物
- [ ] PROTOCOL-V2.md 同步（双端引用一致）
- [ ] 配置项齐全，enabled=false 回滚路径验证（关 MQ 后纯 HTTP 闭环仍通）
- [ ] 交付物：`MQ-ENV-NOTES.md` + `阶段2-实施记录.md`（改动清单/剧本证据/降级取舍/遗留），路径 document/block-records/（沿用 S1 格式）

## 7. 交付物清单

| 文件 | 内容 |
|---|---|
| MQ-ENV-NOTES.md | A3 探测输出、选型结论、topic 创建命令 |
| 阶段2-实施记录.md（block-records） | 改动清单、F3 剧本证据、CI 策略结论（含降级证据若有）、遗留与边界 |
| 代码 | 见 §4（pom/yml/新类/测试） |
| 契约 | PROTOCOL-V2.md MQ 小节 |

## 8. 代码基线清单（定位复用点，先读后写）

- reason-main：
  - `modules/device/controller/DeviceEventController.java`（HTTP 入口参照：验签→编排的顺序）
  - `modules/device/config/DeviceChannelAuthenticator.java`（验签复用）
  - `modules/device/config/DeviceSignature.java`（canonical/verify 复用）
  - `modules/device/service/impl/DeviceEventServiceImpl.java`（编排复用，勿重写）
  - `modules/device/form/DeviceEventForm.java`（消费构造的 form）
  - `src/test/java/com/reason/modules/device/service/DeviceEventServiceImplTest.java`（测试风格参照）
  - 现有 Testcontainers IT（查 `src/test` 容器测试结构，F2 照抄）
- reason-barrier-sim：
  - `reporter/EventReporter.java` + `reporter/HttpEventReporter.java`（接口与发送语义参照——异步单线程保序/退避重试/4xx 不重试）
  - `config/DeviceSignature.java`、`config/SimProperties.java`（签名/配置风格）
- 契约：contracts/PROTOCOL-V2.md §3.1（签名与 MQ 迁移注记）
- 路线出处：红队文档 §6 阶段 2 / §8 D3-D7

## 9. 阻塞项处理

规格未覆盖、复用点冲突、探测结果与 §4-A3 分支不符：**停手**，在交付物"阻塞与待确认"章节记录（问题/代码位置/你的建议），不得自行扩展决策。开发完成后整体移交主 agent 过目（用户指定流程）。
