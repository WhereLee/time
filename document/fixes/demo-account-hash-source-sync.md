# 修复：demo 账号哈希源头落后（db/reason-faster.sql）

> 状态：已修（2026-09-04，A2 块——源头与本地库对齐）
> 关联：demo 数据漂移事件（README 密码 admin123 vs sql 遗留 SHA-256）

## 缺陷描述

`db/reason-faster.sql` 中 adminManager 的密码哈希是遗留 SHA-256（`16de7617...`），
而本地开发库早已在 BCrypt 迁移时更新为 `$2a$10$...`——**源头文件落后于数据库**：
每次从 sql 重建库都会复现「README 密码 admin123 登不进」问题。

性质：**只修了现象（本地库），没修源头（建库脚本）**——测试基建 A2 块要求
CI 每次用全新容器从 sql 建库，此缺陷会直接让集成测试登录失败，暴露并触发修复。

## 修复

二进制替换 sql 中 adminManager 哈希段为已验证的 BCrypt 值
（`$2a$10$SQvSgY15cXHKG4xu02Q7V.JLVxNkLrnJGB3RCElzu4dBeqQOAEBz.`），
替换前先做编码判定（脚本确认文件为合法 UTF-8，与文件头 65001 声明一致）。

## 附带发现

- sql 文件本身是合法 UTF-8——此前 PowerShell 读取中文乱码是**读取端用 GBK 解码**的假象，非文件问题。

## 教训

1. **数据修复要问「源头在哪」**：demo 数据存在于三个地方（sql 文件 / 本地库 / README 文档），
   修任何一处都要检查其余两处的漂移。
2. 建库脚本是 CI 测试的输入——测试驱动下源头滞后会被自动化立刻暴露（本次即由 A2 设计暴露）。
