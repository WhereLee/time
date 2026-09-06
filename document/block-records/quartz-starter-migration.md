# 块记录：Quartz 裸依赖 → spring-boot-starter-quartz 迁移

> 块循环：设计讨论 → 拍板 → 实现 → 回归 → 沉淀（2026-09-04）
> 验证结果：本地启动回归通过（编译全绿 + 应用启动 5.9s + 调度器 30s 延时后真实启动 + 登录/带 token 链路 code 0）

## 背景与决策

壳子用裸 `org.quartz-scheduler:quartz` + 自研 `ScheduleConfig.java`（60 行手写 Properties），
导致：配置锁死在代码里、`spring.quartz.*` 配置树无人消费（测试想禁 job 禁不了）。

决策：迁移 `spring-boot-starter-quartz`（Boot 官方集成）——性质为删自研等价物、回标准路径；
版本由 Boot BOM 管理（与现状同为 2.3.2，无版本变化）。时机：业务为零 + CI 装配未定型。

## 交付清单

1. pom：quartz 裸依赖 → starter；删除孤儿 `quartz.version` 属性
2. `application.yml` 新增 `spring.quartz` 子树（配置平移表）：
   - `job-store-type: jdbc` → Boot 装配 LocalDataSourceJobStore（等价原自研选择，注释中写明）
   - scheduler-name / startup-delay: 30s / overwrite-existing-jobs → SchedulerFactoryBean 等价属性
   - `properties.org.quartz.*`：instanceId/threadPool/cluster 等逐条平移
   - selectWithLockSQL 修正（见 fixes/quartz-selectwithlocksql-mysql-dialect.md）
3. 删除 ScheduleConfig.java

## 核对与取舍

- **applicationContextSchedulerContextKey 丢弃**：核对了 job 模块全部消费者
  （ScheduleJob 执行器用 SpringContextUtils 静态获取 Bean，不读 schedulerContext）——无消费者，安全丢弃
- **job-store-type: jdbc** 是 Boot 配置 LocalDataSourceJobStore 的标准途径
  （原注释内容即 "改为 spring LocalDataSourceJobStore"——语义完全对齐）
- 消费方代码零改动（ScheduleUtils/ScheduleJob/ScheduleJobServiceImpl 的 Scheduler Bean 由 starter 提供）

## 暴露的潜伏缺陷（详见 fixes）

- SQLServer 方言 `UPDLOCK` 泄漏进 MySQL 集群锁 SQL——因调度器从未活过 30 秒延时而未爆过；
  本次启动回归（等待 35 秒 + 观察日志）确认修复。

## 回归验证记录

- `mvn clean test`：编译全绿（ScheduleConfig 删除无残留引用）
- 应用启动 5.9s → 等待 40s → 日志：`Starting Quartz Scheduler now, after delay of 30 seconds`
  → `ClusterManager: detected 1 failed or restarted instances`（正常：上次实例的 STATE 记录恢复）
  → `Scheduler ReasonScheduler_$_LAPTOP-... started`（集群注册成功，无 SQL 错误）
- HTTP 回归：登录 code 0、带 token 访问受保护接口 code 0（Security 链路未受影响）
- 5 条含 "ERROR" 的日志为 logback appender 名 ERROR_FILE 的误报（日志文件名），非真实错误
