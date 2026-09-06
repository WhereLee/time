# 坑：纯单测下 MP Lambda 包装器报 "can not find lambda cache"

> 状态：已踩·已修（2026-09-04，P2 块，ParkSessionServiceImplTest 首跑 5 个用例全炸）
> 适用：任何在无 Spring 上下文的纯 Mockito 单测里触发 MyBatis-Plus Lambda 包装器的测试

## Bug 类别

框架元数据未初始化——单测环境与框架假设（Spring 上下文已装配 MybatisConfiguration）不符。

## 现象

测试执行到构造 `LambdaQueryWrapper`/`LambdaUpdateWrapper`（`.eq(Entity::getXxx, ...)`）时报：

```
MybatisPlusException: can not find lambda cache for this entity [com.reason...ParkSpaceEntity]
```

症状分化：直接执行到包装器构造的用例报 ERROR；把异常包进 `assertThatThrownBy` 断言期望 RRException 的用例报 FAILURE（实际捕获的是 MybatisPlusException，类型不匹配）——**同一根因、两种报错形态**。

## 根因

Lambda 包装器的方法引用（`Entity::getSpaceId`）依赖 **SerializedLambda + TableInfo 元数据缓存**把 getter 反解成列名；该缓存由 MybatisConfiguration 在 MyBatis 装配（Mapper 注册）时填充。**纯单测没有 Spring 上下文、Mapper 从未注册**——缓存为空，方法引用无法反解。

## 修复模式

`@BeforeAll` 手动初始化被测实体的 TableInfo：

```java
@BeforeAll
static void initMybatisPlusLambdaCache() {
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
    TableInfoHelper.initTableInfo(assistant, ParkSpaceEntity.class);
    TableInfoHelper.initTableInfo(assistant, ParkSessionEntity.class);
}
```

覆盖被测 Service 实现里 Lambda 包装器引用到的**全部实体**（漏一个炸一个）。

## 教训

1. 写 MP Service 的 mock 单测前，先把"Service 内构造了哪些实体的 Lambda 包装器"列出来，全部 init——别等首跑炸了再补
2. assertThatThrownBy 捕获到"预期之外异常类型"时先看**实际抛了什么**（报告里有完整堆栈），MybatisPlusException 这类框架异常在单测里几乎都指向环境缺失而非业务 bug
3. 与 SpringBootTest 集成测试（上下文齐全）无此问题——只有纯单测需要
