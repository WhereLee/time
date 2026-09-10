# scripts/verify —— 验证剧本与证据归档

升降杆样例（批次 2-6）的端到端验证剧本（Windows PowerShell 5.1），与 `document/block-records/` 一一对应：
**block-record 记录结论，本目录保存产生结论的脚本与运行证据**（`*_out.txt` / `*_log.txt` 为当时运行输出，历史留档）。

> 脚本内容全英文：PS 5.1 对无 BOM UTF-8 文件按 GBK 解析，中文注释会破坏语法（教训见 `document/pitfalls/`）。
> **S7 的演示解密口令不入库**：运行前通过环境变量 `JASYPT_DEMO_KEY` 注入（未设置时脚本 exit 2）；归档证据中的口令已脱敏为 `<env-injected>`。

## 剧本清单

| 目录 | 批次 | 剧本（按运行顺序） | 结论落档 |
|---|---|---|---|
| batch2/ | 批次2 批量设备与注入器补全 | `_g1_register`（50 台注册 + 密钥文件生成）→ `_g2_precheck` / `_g2_flood` / `_g2_reset`（心跳洪峰）→ `_g3_inject`（注入器：乱序/重放/延迟） | block-records/批次2-批量设备与注入器补全.md |
| batch3/ | 批次3 量下回归与通道终态 | `_g4_s0`-`_g4_s4`（心跳延迟 / 事件延迟丢包 / 单向断 60s / broker 故障）+ `_g4_verdict`（终态收口）；`_g4_observe` / `_g4_evidence` / `_g4_fix_seq`（Redis 回退事故取证与修复） | block-records/批次3-量下回归与通道终态.md |
| batch4/ | 批次4 告警增强与任务看护 | `_g4b_p1`-`_g4b_p4` + `_g4b_verdict`（批量离线合并 / 任务停摆 / 指标 / 事故复演） | block-records/批次4-告警增强与任务看护.md |
| batch5/ | 批次5 横切治理与毫秒化 | `_g5_s1`-`_g5_s9`（配置扫描 / 认证防护 / 入口限流 / 审计降级 / 双时域清理 / 毫秒闭环 / jasypt 闭环 / 无 profile fail-fast / CORS 双向矩阵）+ `_g5_fulltest_out.txt`（全量单测 119/119 输出）与 `_g5_s5_out_prerestart_repro.txt`（缺陷现场实证） | block-records/批次5-横切治理与毫秒化.md |
| batch6/ | 批次6 工程台账与样例交付化 | `_g6_backup_restore`（备份/恢复本地演练：mysqldump 全量 + Redis RDB，活库区间断言）+ `_g6_mq_flood`（MQ 形态事件洪峰：offset 增量/积压归零/100 命令闭环/双日志零增量） | block-records/批次6-样例交付化.md |

## 环境前置

脚本按作者本机环境硬编码了路径与端口，换机复跑需调整脚本顶部的 `$repo` / `$outPath` 及中间件路径：

- Windows + PowerShell 5.1；平台 `:8200`（dev profile）+ 模拟器 `:8300` + MySQL 8 + Redis（`F:\Redis\redis-cli.exe`，db15）
- MQ 三进程（namesrv 9876 / broker 10911 / proxy 8081，环境笔记见 block-records/MQ-ENV-NOTES.md）——批次 3 起剧本依赖
- batch6 起用 MQ 运维工具采样：`mqadmin topicStatus`（写入 Max Offset）与 `consumerProgress -g platform-device-event -t device-event`（Diff / Inflight / TPS）；**裸 `-g` 不带 `-t` 会因 gRPC pop 消费组无 `%RETRY%` 路由而失败**（探测结论同见 MQ-ENV-NOTES.md）
- 平台管理员种子账号（见 `db/reason-faster.sql`）；`_g1_register` 会生成 `reason-barrier-sim/batch-secrets.properties`（已 gitignore，不入库）
- S7 另需 `JASYPT_DEMO_KEY`（演示口令在仓库外保管）

## 运行方式

各批次按剧本编号顺序执行（如 batch5：s1 → s9）；证据默认写入脚本顶部 `$outPath` 指定的路径。
运行前确认：平台/模拟器/MQ 处于对应批次的形态（各 block-record"环境形态"一节有记录）。
