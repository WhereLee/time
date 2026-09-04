# document/ —— 项目文档目录

> 组织原则（2026-09-04 确立）：**一个问题一个文件**，禁止把所有内容堆进单个大文档。

| 目录 | 内容 | 命名规则 |
|---|---|---|
| `block-records/` | 块循环每块的阶段记录（做了什么、取舍、验证结果） | `A{n}-{主题}.md` |
| `pitfalls/` | 踩坑记录（Bug 类别 / 根因 / 修复模式 / 教训，状态标签） | 英文短横线主题名 |
| `fixes/` | 缺陷修复记录（来源缺陷、修复方案、回归验证） | 英文短横线主题名 |
| `knowledge/` | 深层知识点（原理、工程价值、边界条件） | 英文短横线主题名 |
| `roadmap.md` | 旧待办索引（历史条目） | — |
| `roadmap/` | 新待办（触发条件 + 重构方案），一待办一文件 | 英文短横线主题名 |

## 当前文件清单

- block-records/A1-测试基建与核心单测.md
- block-records/quartz-starter-migration.md
- block-records/security-debt-cleanup.md
- block-records/observability-baseline.md
- pitfalls/mockito-nested-stubbing.md
- pitfalls/mock-default-value-by-return-type.md
- pitfalls/passwordcodec-byte-order.md
- pitfalls/testresttemplate-contextpath-auto-prefix.md
- pitfalls/value-yaml-list-binding.md
- fixes/authservice-npe-user-not-found.md
- fixes/demo-account-hash-source-sync.md
- fixes/quartz-selectwithlocksql-mysql-dialect.md
- knowledge/mockito-strict-stubs.md
- roadmap/login-attempt-guard-extraction.md
- roadmap/async-mdc-taskdecorator.md
