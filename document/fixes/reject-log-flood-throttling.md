# 修复：拒绝路径日志放大（洪峰下 warn 无配额）

> 状态：**已修并实测**（2026-09-11，批次8 P3——用户追加授权；原为批次7 B4 的"已声明边界"；S3 同场景重跑 warn 6056 → 32 行，-99.5%）
> 来源：批次7 B4 容量画像成本侧观察（document/knowledge/heartbeat-event-capacity-profile.md）

## 问题描述

B4 S3（假签名事件洪水 120rps×60s）实测：6056 个过闸 401 各产生一条 warn
（`RRExceptionHandler.handleResponseStatus` 状态透传），60 秒灌入 warn.log 6056 行/约 1.3MB，
即 **≈100 行/秒、1.3MB/分钟**；429 路径更甚（`DeviceTrafficGuard` 自身一条 + 透传一条）。

限流/验签把"业务与 MQ"保护住了，但**日志侧无配额**——攻击者把速率控制在闸内即可持续放大
磁盘与 IO。这是"保护了系统但保护不了磁盘"的成本侧缺口。

## 修复

新增 `LogThrottle`（`common/utils`），对重复拒绝日志做"软配额"：

- **per-key 5s 窗口**：窗口内最多输出一条；被抑制条数累计，下次放行时附于尾部
  （"窗口内另抑制同类日志 N 条"）——可见性 = 首条样本 + 计数；
- **键数上限 1024**：超出归并到溢出键共享窗口——随机 deviceNo 刷日志时输出量仍有上界，
  计数器不无界增长；
- **Supplier 惰性消息**：被抑制时不构造任何字符串（拒绝热路径零额外开销）；
- **CAS 放行**：并发同窗口仅一条输出。

接入点：

| 调用点 | 键 | 说明 |
|---|---|---|
| `RRExceptionHandler.handleResponseStatus` | `http-status:{status}` | 401/429/400 各自窗口（假签名/超频/协议垃圾） |
| `DeviceTrafficGuard.checkDeviceBucket` | `traffic-dev:{类别}:{设备号}` | 单设备洪峰每设备每窗口一条 |
| `DeviceTrafficGuard.assertEventAllowed` 全局闸 | `traffic-global` | 全局水位拒绝合流 |

## 效果与边界

- **上界推导**：输出量从 O(请求) 降为 O(键数 × 窗口数)。S3 形态（单类攻击）从 6056 条
  降到约"每 5s 一条 + 计数"；随机设备号形态由溢出键兜底（仍约每 5s 一条）。
- **S3 已重跑实测（2026-09-11 01:14，重启平台加载 P3 构建，同参数 120rps×60s）**：warn 输出
  **6056 → 32 行（-99.5%，+7.1KB）**——3 键（401/429/全局闸）× ~12 窗口 ≈ 36 理论值与 32 实测
  精确吻合；响应分布零变化（401=6035 / 429=925，与修复前一致）；首条样本保留个体信息
  （`设备签名无效: BARRIER-B-24`）与抑制计数（`窗口内另抑制同类日志 502 条`）；洪水后生命线 50/50。
  证据：`scripts/verify/batch8/_b8_s3_rerun_out.txt`。
- 只做日志侧配额：不改业务状态码、不吞响应、不影响限流判定链路；
- 溢出键合流后个体 deviceNo 只见窗口首条样本（个体信息有损，聚合可见性保留）——取舍如实声明。

## 回归验证

- `LogThrottleTest` 7 例：窗口内首条+计数、窗口过期带计数、首条无后缀、被抑制零构造、
  键独立、溢出兜底、并发 CAS 单放行且抑制计数无丢失；
- `DeviceTrafficGuardTest` 6 例回归绿（构造注入节流器，判定逻辑不变）。

## 关联

- document/knowledge/heartbeat-event-capacity-profile.md（成本侧观察 + 已修标注）
- document/plans/批次8-五项修复-方案.md 方案 6
