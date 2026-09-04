# 坑：PasswordCodec 字节序——取证结论对了，实现忘了改

> 状态：已踩·已修（2026-09-04，A1 块测试断言暴露）
> 适用：任何"先实现、后取证、再回改"的工作流

## Bug 类别

实现与已验证结论不一致——文档/结论正确，代码保持错误状态。

## 现象

`PasswordCodecTest.遗留SHA256格式_校验通过_与Shiro位级兼容` 断言失败。若此代码直接进入生产：**全部存量 SHA-256 用户无法登录**（渐进迁移机制静默失效）。

## 根因

时间线错位：

1. Security 迁移时**先写了实现**：`sha256WithSaltSuffix` 按 pwd‖salt 顺序
2. 登录验证失败后**做了取证**：用真实 shiro-core-2.0.0.jar 对比，确认 Shiro SimpleHash 语义是 **salt‖pwd**（盐在前）
3. 用临时脚本（TestShiroHash）验证了正确顺序——**但没有回头改 PasswordCodec 的实现**，且临时脚本随后被删除

结论"浮"在人脑和一次性脚本里，没有物化到生产代码。

## 修复

```java
//字节序与 Shiro SimpleHash 一致：先 salt 后 password（取证验证，顺序颠倒则存量用户无法登录）
md.update(legacySalt.getBytes(StandardCharsets.UTF_8));
md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
```

同时把取证向量（salt=`CYiKIzx4410U9yaBPBHE`、pwd=`admin123` → `06bf8058...`）**固化为 PasswordCodecTest 的自动化断言**——位级兼容结论从"人说过"变成"测试持续验证"。

## 教训

1. **"验证过的结论"必须物化为持续执行的断言**，不能停留在一次性脚本或对话记录里。取证脚本删除之前，先问：结论落进测试了吗？
2. 调试两个变量（字节序、demo 数据漂移）叠加的问题时，先用隔离实验逐个排除，再回改实现——回改是独立步骤，取证结束 ≠ 修复完成。
3. 这是"测试抓 bug 而非人眼"的实证：字节序差异在 hash 字符串里人眼根本看不出来。
