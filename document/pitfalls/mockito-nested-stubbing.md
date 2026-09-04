# 坑：Mockito 嵌套 stub 导致 UnfinishedStubbingException

> 状态：已踩·已修（2026-09-04，A1 块，**同一会话内踩了两次**）
> 适用：任何使用 Mockito 的测试代码

## Bug 类别

测试框架状态污染——编译期与常规断言均无法暴露，只能靠运行时报错。

## 现象

- 测试抛 `UnfinishedStubbingException: E.g. thenReturn() may be missing`
- **报告的行号是"下一个 mock 交互发生的位置"，不是真正写错的那一行**——真正的 unfinished stub 在更早的语句，残留状态跨语句、跨方法污染整个测试类

## 根因

`when()` 的参数求值期间执行了**其他 stub 调用**。典型触发模式：

```java
// 反例：thenReturn 的参数里调用 helper，helper 内部又有 when()
when(sysUserDao.getUserByLoginname(LOGINNAME)).thenReturn(userMock("hash", "salt"));

private SysUserEntity userMock(String hash, String salt) {
    SysUserEntity u = mock(SysUserEntity.class);
    when(u.getUserId()).thenReturn(2L);   // ← 在外层 when 未完成时发生的新 stub
    ...
}
```

Mockito 的 stubbing 是**线程局部状态机**：`when(X)` 开始 → 期待 `thenReturn` 收尾。参数求值发生在两者之间，此时插入新的 stub 调用即破坏状态机。

## 修复模式

**先准备返回值，再写 when**——helper 调用永远提到 when() 之外：

```java
// 正例
SysUserEntity user = userMock("hash", "salt");
when(sysUserDao.getUserByLoginname(LOGINNAME)).thenReturn(user);
```

## 教训

1. 这个模式的惯性极强：本次会话修复了 6 处之后，新写的 `thenReturn(errorPathUserMock())` 又踩了一次。**凡 helper 返回 mock，一律不得进入 thenReturn 参数位**。
2. UnfinishedStubbing 的报错位置有迷惑性，排查时看"该行之前最近的 stub 语句"。
3. 一次性修复后应 grep 全测试源码确认无同类模式（`thenReturn(.*Mock\(` / `thenReturn(.*Entity\(\)`）。
