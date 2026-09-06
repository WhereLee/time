# 坑：Mock 默认值随返回类型变化（primitive 0 / 对象 null / 集合空集合）

> 状态：已踩·已修（2026-09-04，A1 块，三层默认值行为叠加成连环坑）
> 适用：mock 对象的方法返回值影响代码控制流时

## Bug 类别

mock 未 stub 方法的默认返回值与业务代码的 null 判断预期不符，导致控制流走进错误分支。

## 现象

`getChangeForce` 内 NPE：`Cannot invoke "Integer.intValue()" because "changeLimit" is null`（227 行），栈帧显示 getChangeForce:218 → getChangeForce:227（看似不可能的"递归"）。

## 根因（三层默认值行为叠加）

1. **实体字段是 primitive `long`**（非包装 Long）：`mock(SysUserEntity.class)` 的 `getUserPwdChangetime()` 默认返回 **0** 而非 null → `if (pwdChangetime == null) return 1;` 的短路判断失效。
2. **Mockito 对集合返回类型默认返回空集合而非 null**（ReturnsEmptyValues 语义）：未 stub 的 `getChangeForceAndLimit()` 返回**空 Map** → `map.get("changeLimit")` 得 null（map 本身非 null，所以 NPE 消息是 changeLimit 而非 map）。
3. 空 Map 无键 → `changeLimit` 拆箱 NPE 于 227 行。

最小复现实证输出：`>>> mock getUserPwdChangetime = 0`——一锤定音。

## 修复模式

- 对**影响控制流**的 mock 一律**显式 stub**，不依赖默认值语义：
  ```java
  when(u.getUserPwdChangetime()).thenReturn(System.currentTimeMillis() / 1000);  // 刚变更过
  when(paramUtils.getChangeForceAndLimit()).thenReturn(Map.of("changeLimit", 30, "changeForce", 2));
  ```
- 排查工具：**最小复现 + 打印 mock 实际返回值**（一个用例只验证一个行为）。

## 教训

1. Mockito 默认值速记：**对象 → null；primitive → 0/false；Collection/Map/数组 → 空集合**。判断分支依赖 mock 返回值时，默认值会静默改变执行路径。
2. 实体字段用 primitive 是历史设计（MyBatis-Plus 场景推荐包装类型以便 null 语义），测试适配比改造实体先走。
3. "看似递归"的栈帧先怀疑行号与源码错位（stale class / 行号记忆偏差），用 `mvn clean` 排除后再读栈。
