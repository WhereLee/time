# document/ —— 项目文档目录

> 组织原则（2026-09-04 确立）：**一个问题一个文件**，禁止把所有内容堆进单个大文档。

| 目录 | 内容 | 命名规则 |
|---|---|---|
| `block-records/` | 块循环每块的阶段记录（做了什么、取舍、验证结果） | `批次{n}-{主题}.md`；早期块记录为自由主题名 |
| `deploy/` | 部署资产：systemd unit、部署脚本、云服务器手册 | `{主题}-{类型}.{sh,md,service}` |
| `pitfalls/` | 踩坑记录（Bug 类别 / 根因 / 修复模式 / 教训，状态标签） | 英文短横线主题名 |
| `fixes/` | 缺陷修复记录（来源缺陷、修复方案、回归验证） | 英文短横线主题名 |
| `knowledge/` | 深层知识点（原理、工程价值、边界条件） | 英文短横线主题名 |
| `roadmap.md` | 旧待办索引（历史条目） | — |
| `roadmap/` | 新待办（触发条件 + 重构方案），一待办一文件 | 英文短横线主题名 |

## 当前文件清单

### 部署资产
- deploy/云服务器部署手册.md（barrier 形态：MQ 三进程/密钥注入/验收清单/备份恢复§8/版本回滚§9/安全清单§10）
- deploy/backup.sh（MySQL 全量 + Redis RDB 定时备份，保留策略；恢复步骤见手册 §8）
- deploy/reason-main.service（平台 systemd unit）
- deploy/reason-barrier-sim.service（设备模拟 systemd unit）

### 块记录（框架演进）
- block-records/quartz-starter-migration.md
- block-records/security-debt-cleanup.md
- block-records/升降杆完全闭环-监控与自动升降.md
- block-records/阶段2-实施记录.md
- block-records/MQ-ENV-NOTES.md
- block-records/B1-可观测性地基.md
- block-records/S1-调度健壮性与双实例实证.md
- block-records/批次2-批量设备与注入器补全.md
- block-records/批次3-量下回归与通道终态.md
- block-records/批次4-告警增强与任务看护.md
- block-records/批次5-横切治理与毫秒化.md
- block-records/批次6-样例交付化.md
- block-records/批次7-质量加固与容量画像.md
- block-records/批次8-五项修复.md

### 踩坑记录
- pitfalls/mockito-nested-stubbing.md
- pitfalls/mock-default-value-by-return-type.md
- pitfalls/passwordcodec-byte-order.md
- pitfalls/testresttemplate-contextpath-auto-prefix.md
- pitfalls/value-yaml-list-binding.md
- pitfalls/mybatis-plus-lambda-cache-unit-test.md
- pitfalls/mybatis-plus-injectmocks-basemapper-unit-test.md
- pitfalls/mockito-verify-overloaded-mapper-method.md

### 缺陷修复
- fixes/authservice-npe-user-not-found.md
- fixes/demo-account-hash-source-sync.md
- fixes/quartz-selectwithlocksql-mysql-dialect.md
- fixes/http-event-missing-required-fields-500.md
- fixes/cross-generation-event-replay.md

### 知识点（技术资产：原理/权衡/边界，一主题一文件）
- knowledge/mockito-strict-stubs.md
- knowledge/password-hash-evolution.md
- knowledge/conditional-update-concurrency.md
- knowledge/money-storage-integer-fen.md
- knowledge/state-machine-table-design.md
- knowledge/delete-strategy-soft-vs-disable.md
- knowledge/rbac-menu-permission-model.md

### 待办
- roadmap/login-attempt-guard-extraction.md（✅ 已完成，批次5 T19）
- roadmap/async-mdc-taskdecorator.md
