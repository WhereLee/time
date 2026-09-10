# 心跳/事件通道容量画像与排障实录（JMeter 实测）

> 实测环境（2026-09-10/11，本地单机）：16 核 / 15.7GB；平台 8200（Java 17 + Spring Boot 3.5.16 + Druid + Redis）+ sim 8300（50 台 batch 设备 3s 心跳）+ RocketMQ 三进程 + 压测客户端 JMeter 5.6.3（与目标同机，数据含客户端竞争成本）。
> 证据：`scripts/verify/batch7/`（压测剧本 `_b4_heartbeat_capacity.jmx` / `_b4_event_flood.jmx` + 四场景统计 `_b4_s1..s3_out.txt`）；原始 jtl 不随仓（体积），统计口径见 _out 文件头。

## 一、场景矩阵与结果

| 场景 | 输入（目标） | 实测吞吐 | 响应分布 | 时延 ms（avg/p50/p95/p99） |
|---|---|---|---|---|
| S1 单设备超频 | 5 线程 × 20rps 打 1 台 | 93.8 rps × 19s | **200=105 / 429=1677** | 3.8 / 3 / 7 / 10 |
| S2a 合法心跳 | 40rps（10 台 × 4rps）× 60s | 39.6 rps × 59.1s | **全 200**（2339） | 4.9 / 5 / 7 / 8 |
| S2b 合法心跳 | 80rps（20 台 × 4rps）× 100s | 79.4 rps × 98.7s | **全 200**（7840） | 4.8 / 4 / 7 / 10 |
| S3 事件洪水 | 120rps（30 台 × 4rps）× 60s | 118.9 rps × 58.7s | **401=6056 / 429=924** | 5 / 4 / 8 / 19 |

> S1-S3 的输入侧每线程 4-20rps 由 JSR223 节流器实现；设备密钥经 `-JcsvPath` 注入（tab 分隔、只存本地、仓库零明文）。

## 二、关键结论

### 2.1 限流双层模型（per-device 桶 + 全局水位闸）与实测精确吻合

- **per-device 桶（5rps / burst 10，心跳+事件共查）**：S1 以 93.8rps（约 19 倍）打击单台设备，通过数 **105 = 5rps × 19s + burst 10**——桶速率逐秒精确放行，其余 1677 个全部 429。超频不会拖垮平台（被拉齐到桶速率），也不会误伤（未超频零拒绝，见 S2a/S2b）。
- **全局水位闸（100rps / burst 200，仅事件）**：S3 稳态段输入 119.7rps，拒绝 19.4rps → **通过 100.3rps = 闸速率 100rps**。429 稳态速率 19.0-19.5/s（逐 10s 窗口实测），事件通道被拉齐到全局水位。
- **拒绝走最前置短路**：429 的 P50=4ms、P99=19ms，与 200 同量级——限流判定在验签与业务之前（代码序：`trafficGuard → authenticator → 业务`），洪水不消耗业务资源。

### 2.2 合法负载画像：80rps 时平台游刃有余

| 维度 | S2b 实测（80rps 稳态 45s 采样窗） |
|---|---|
| 错误率 | 0（7840/7840） |
| 时延 | avg 4.8ms / p99 10ms（含 JMeter 同机竞争） |
| 年轻代 GC | 8 次 / 45s，平均暂停 **1.1ms**，GC 时间占比 **0.02%** |
| Full GC | **0 次**；老年代 59.3-59.6MB（**几乎静止**，无晋升压力/泄漏迹象） |
| 进程 CPU | avg 24.6% / max 74%（16 核，即稳态约 4 核当量） |
| 线程状态（jstack 中段） | 无 BLOCKED、无死锁；RUNNABLE 48 与 WAITING/parking 50 为主 |

心跳路径以 Redis 时间戳更新与内存态为主，压测实测瓶颈不在数据库与 GC。按 CPU 线性外推，本机形态的安全余量在 **数百 rps 量级**（线性外推仅供量级参考，非线性拐点未测——如实声明）。

### 2.3 生命线隔离被洪水实测证实

S3 事件洪水（120rps，全部假签名）期间：

| 时点 | redis `barrier:online` | metrics onlineRate | unhandledAlarms |
|---|---|---|---|
| baseline | 50/50 | 100.0% | 0 |
| t+20s（洪水中） | 50/50 | 100.0% | 0 |
| t+45s（洪水中） | 50/50 | — | — |
| post-flood | 50/50 | 100.0% | 0 |

按设计心跳**豁免全局闸**：事件通道被打满 100rps 水位时，心跳生命线吞吐、时延、在线率零变化。同时 401=6056 全部止步验签——洪水**从未进入业务层与 MQ**（零业务副作用、零消息污染）。

## 三、排障实录：一次"压测脚本假象"的三段定位

首跑 S2a 出现 **1×500 + 236×401（全部集中在单一线程）**——若只看全局错误率（10%）会误判为平台缺陷。定位链：

| 段 | 现象 | 判读 |
|---|---|---|
| ① 按线程切分 | 401 全部落在一个线程（236 ≈ 该线程 60s×4rps 全额） | 不是平台问题，是该线程**从未绑定成功**（deviceNo/sign 为空） |
| ② 找 500 真身 | `IndexOutOfBoundsException: Index -1`，`lines.get(idx)` 的 `idx=(getThreadNum()-1)%pool` | 首个线程 `getThreadNum()=0` → idx=-1 → OnceOnly 绑定崩 → 该线程全程无签名 |
| ③ 探针实证 | 4 线程探针打印 `ctx.getThreadNum()`：`1-1→0, 1-2→1, 1-3→2...` | **JMeter 5.6.3 的 getThreadNum() 是 0-based**（与直觉"1-based"相反，且 OnceOnly/PreProcessor 中一致） |
| ④ 修复与验证 | 首修"clamp 到 1"引入新假象：1-1 与 1-2 同映射到设备 1（8rps>5rps 桶）→ 169×429 | 改 `Math.floorMod(tn, pool)`——0-based/1-based 下都均匀映射；重跑 S2a 全 200 |

**教训（已固化为剧本注释）**：压测结果的异常**先按线程名切分再看全局**——单线程异常集中 = 客户端侧假象，全局均匀 = 服务端行为。JSR223 绑定脚本的线程→设备映射必须用 `floorMod` 而非 `%`（负数取模陷阱）。

## 四、复现方法

```
# 心跳容量（S2a: 40rps -> -Jthreads=10 -JdevicePool=10；S2b: 80rps -> 20/20）
jmeter.bat -n -t scripts/verify/batch7/_b4_heartbeat_capacity.jmx ^
  -JcsvPath=<tab-separated deviceNo/secret file (local only)> ^
  -Jthreads=10 -JdevicePool=10 -Jthread_rps_x100=400 -Jduration=60 ^
  -l s2a.jtl -j jmeter-s2a.log

# 事件洪水（S3: 120rps 假签名，30/30）
jmeter.bat -n -t scripts/verify/batch7/_b4_event_flood.jmx ^
  -JcsvPath=<...> -Jthreads=30 -JdevicePool=30 -Jthread_rps_x100=400 -Jduration=60 ^
  -l s3.jtl -j jmeter-s3.log
```

- `-l` 目标文件**不覆盖**（重跑换文件名）；设备池上限=CSV 行数，线程数>池数时按 floorMod 复用（每设备多线程会叠加到桶上，属预期）。
- S1 单设备超频用 `-Jthreads=5 -JdevicePool=1 -Jthread_rps_x100=2000 -Jduration=20`。
- 事件洪水剧本**故意用固定假签名**：验证的语义是"限流先于验签"（401 与 429 的混合分布），且不触达业务/告警/MQ。
