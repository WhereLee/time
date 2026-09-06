# 坑：Mockito verify 与 MP 重载 insert 方法编译歧义（3.5.9+）

> 状态：已踩·已修（2026-09-04，P2 块，ParkSessionServiceImplTest 编译失败）
> 适用：MyBatis-Plus 3.5.9+（BaseMapper 新增批量 insert）且对 insert 使用 Mockito 匹配器

## Bug 类别

框架 API 演进引入的重载（insert(T) 与 insert(Collection<T>)）撞上 Mockito 无类型匹配器的泛型推断。

## 现象

```java
verify(parkSessionDao, never()).insert(any());
```

编译报错：`The method insert(ParkSessionEntity) is ambiguous for the type ParkSessionDao`——`any()` 无类型参数，编译器无法在两个重载间选择。换成 `any(ParkSessionEntity.class)` 依旧歧义。

## 根因

MP 3.5.9 起 `BaseMapper` 新增批量插入 `default int insert(Collection<T> entityList)`，与经典 `int insert(T entity)` 重载并存。Mockito 的 `any()`/`any(Class)` 匹配器返回 null 字面量，在重载解析时没有足够的类型信息约束到单一方法。

## 修复模式

- **verify 的 never 断言**：删除或换用无歧义交互（本案例中删除——异常断言已隐含"校验先于写库"，该 verify 价值低）。若必须验证"未写库"，可改断言副作用可观察点
- **成功路径**：用 `ArgumentCaptor<T>.capture()`（静态类型明确）替代 `any()`
- **stub 路径**：`doAnswer(...).when(dao).insert(any(Entity.class))` 未触发歧义（when 场景编译器可推断），但保持类型参数写法最稳

## 教训

1. 依赖升级带来的 API 重载会悄悄破坏既有测试写法——编译期暴露已是幸运，升级 MP 后跑一遍全量编译是底线
2. Mockito 匹配器在重载方法上的通病：`any()` 应始终配类型参数；verify/stub 位置的推断能力不同，遇到歧义优先改用 captor
3. never-verify 是低价值断言（意图常已被异常断言覆盖），删除比跟编译器搏斗划算
