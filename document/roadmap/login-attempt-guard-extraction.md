# 待办：LoginAttemptGuard 抽取重构（登录尝试锁定逻辑）

> 状态：待触发
> 登记时间：2026-09-04
> 触发条件：登录 / 口令锁定逻辑需要变更时（变更前先完成本重构）

## 背景

`SysUserServiceImpl.login` 方法约 200 行，内聚了三块职责：

1. IP 白名单校验
2. 口令尝试次数计数与限时锁定（Redis key：`user_{loginname}`）
3. 密码验证与登录后置处理（token 发放 / BCrypt 渐进升级 / 登录时间更新）

## 决策（测试基建块 A1）

A1 只测关键分支（BCrypt 登录成功 / 遗留用户登录触发升级 / 锁定触发 / 次数累加），**不做** LoginAttemptGuard 抽取。

理由：测试先行的安全重构优于无保护的顺手重构；当前无业务变更压力。

## 重构方案（触发时执行）

- 抽取 `LoginAttemptGuard` 组件：`isLocked(loginname)` / `onFailure(loginname, limit, lockTime)` / `onSuccess(loginname)`
- 先为 LoginAttemptGuard 写 4 个单测，再迁移 login 的调用，最后删除 login 内联逻辑

## 影响

- login 单测的 mock 量偏大（RedisUtils 交互断言），当前可接受
