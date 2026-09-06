# 修复：Quartz selectWithLockSQL 携带 SQLServer 方言（UPDLOCK）——迁移时暴露的潜伏缺陷

> 状态：已修（2026-09-04，Quartz starter 迁移块，本地启动回归验证）
> 潜伏期：自壳子引入 MySQL 支持起（从未被触发，因为调度器从未真正启动过）

## 缺陷描述

`ScheduleConfig.java` 中硬编码了集群锁 SQL：

```java
prop.put("org.quartz.jobStore.selectWithLockSQL", "SELECT * FROM {0}LOCKS UPDLOCK WHERE LOCK_NAME = ?");
```

`UPDLOCK` 是 **SQL Server 的锁提示语法**，MySQL 不识别——集群模式调度器启动时
ClusterManager 线程执行该 SQL 必然语法错误，导致集群注册/恢复失效。

## 为什么潜伏这么久没爆

两个条件叠加：
1. `SchedulerFactoryBean.setStartupDelay(30)`——调度器延时 30 秒才启动；
2. 历史验证（端到端登录、Security 迁移验收）都在应用启动后几秒内完成并停止应用——**调度器从未活过 30 秒**。

结论：**"应用启动成功"≠"调度器启动成功"**——延时启动的组件必须在延时之后验证。

## 修复

MySQL 正确语义（Quartz 默认锁 SQL 即 FOR UPDATE 版）：

```yaml
org.quartz.jobStore.selectWithLockSQL: "SELECT * FROM {0}LOCKS WHERE LOCK_NAME = ? FOR UPDATE"
```

迁移到 starter 后回归验证：启动 35 秒后观察日志
`Scheduler ReasonScheduler_$_LAPTOP-... started` + ClusterManager 扫描记录——无 SQL 错误。

## 教训

1. **手写框架配置里藏着方言 SQL**——renren 血统基于 SQLServer 主库（多数据源注释可证），
   移植到 MySQL 时这类方言常量最易漏网；核对老配置要逐个属性查语义，不能只查格式。
2. **验证时序盲区**：延时启动/异步启动的组件，常规"启动即验"无法覆盖——回归时要等到延时过后。
3. 迁移 1:1 平移配置是发现时机：把每个属性翻译成目标环境语义时，方言差异自然现形。
