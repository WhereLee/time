# document/ —— 项目文档目录

> 组织原则（2026-09-04 确立）：**一个问题一个文件**，禁止把所有内容堆进单个大文档。

| 目录 | 内容 | 命名规则 |
|---|---|---|
| `block-records/` | 块循环每块的阶段记录（做了什么、取舍、验证结果） | `A{n}-{主题}.md` |
| `deploy/` | 部署资产：systemd unit、部署/验收脚本、云服务器手册 | `{主题}-{类型}.{sh,md,service}` |
| `pitfalls/` | 踩坑记录（Bug 类别 / 根因 / 修复模式 / 教训，状态标签） | 英文短横线主题名 |
| `fixes/` | 缺陷修复记录（来源缺陷、修复方案、回归验证） | 英文短横线主题名 |
| `knowledge/` | 深层知识点（原理、工程价值、边界条件） | 英文短横线主题名 |
| `roadmap.md` | 旧待办索引（历史条目） | — |
| `roadmap/` | 新待办（触发条件 + 重构方案），一待办一文件 | 英文短横线主题名 |

## 当前文件清单

- 项目总体计划-智慧充电运营平台.md（总纲：叙事/资产/蓝图/块路线）

### 部署资产
- deploy/云服务器部署手册.md（M0 P6：环境/安全清单/部署/密钥管理）
- deploy/reason-main.service、deploy/reason-device-sim.service（systemd unit）
- deploy/m1-deploy.sh（M1 部署：导 04-07 SQL + 换 jar + 起双服务）
- deploy/m1-verify.sh（M1 云上验收脚本 v3：V1-V5 31 项断言 + cron 恢复 + 数据清理）

### 阶段计划
- plans/M0-停车域最小闭环计划.md（M0 执行计划：验收/边界/模型/接口/小步划分）——已完成（09-05，见 P6 块记录）
- plans/M1-充电域与跨方优惠联动计划.md（M1 执行计划：跨方凭证化协议/减免计费/调度转正/小步 M1-1..9）——✅ 已完成（09-05，验收 31/31，见 M1 块记录）

### 块记录
- block-records/A1-测试基建与核心单测.md
- block-records/quartz-starter-migration.md
- block-records/security-debt-cleanup.md
- block-records/observability-baseline.md
- block-records/P1-停车域表结构与实体.md
- block-records/P2-会话状态机与并发正确性.md
- block-records/P3-出场结算与订单快照.md
- block-records/P4-管理端HTTP层与权限生效.md
- block-records/P5-device-sim设备模拟双进程闭环.md
- block-records/P6-云部署收口与M0验收.md
- block-records/M1-充电域与跨方优惠联动.md
- pitfalls/mockito-nested-stubbing.md
- pitfalls/mock-default-value-by-return-type.md
- pitfalls/passwordcodec-byte-order.md
- pitfalls/testresttemplate-contextpath-auto-prefix.md
- pitfalls/value-yaml-list-binding.md
- pitfalls/mybatis-plus-lambda-cache-unit-test.md
- pitfalls/mybatis-plus-injectmocks-basemapper-unit-test.md
- pitfalls/mockito-verify-overloaded-mapper-method.md
- fixes/authservice-npe-user-not-found.md
- fixes/demo-account-hash-source-sync.md
- fixes/quartz-selectwithlocksql-mysql-dialect.md

### 知识点（面试素材：原理/权衡/边界，一主题一文件）
- knowledge/mockito-strict-stubs.md
- knowledge/password-hash-evolution.md
- knowledge/conditional-update-concurrency.md
- knowledge/billing-snapshot-invariant.md
- knowledge/money-storage-integer-fen.md
- knowledge/state-machine-table-design.md
- knowledge/delete-strategy-soft-vs-disable.md
- knowledge/rbac-menu-permission-model.md

### 待办
- roadmap/login-attempt-guard-extraction.md
- roadmap/async-mdc-taskdecorator.md
