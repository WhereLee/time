# 知识点：Mockito Strict Stubs 的工程价值

> 关联：A1 块（MockitoExtension 默认开启 STRICT_STUBS）
> 深度标记：理解"为什么默认严格"背后的工程权衡

## 严格模式做了什么

`@ExtendWith(MockitoExtension.class)` 下，Mockito 默认 STRICT_STUBS，带来两类强约束：

1. **UnnecessaryStubbingException**：stub 了但用例从未消费的交互 → 测试类结束时报错
2. **PotentialStubbingProblem**：真实调用与 stub 的参数不匹配 → 立即报错（而非静默返回默认值）

## 工程价值（宽松模式会掩盖什么）

### 1. 测试精确性成为强制约束

宽松模式下可以随手 stub 一堆"可能用到"的方法，测试照常通过。严格模式迫使每个 stub 都被消费——**测试因此精确反映被测代码的真实依赖面**。

本块的实证：login 的错误路径在 `open()`/`getUserId()` 消费点**之前**就抛异常，复用成功路径的 userMock 必然产生未消费 stub。解法是独立的 `errorPathUserMock()`——**错误路径只 stub 错误路径消费的方法**。这个设计是严格模式"逼"出来的，不是主动想到的。

### 2. 重构死代码探测器

被测代码删除某个分支后，对应 stub 变成未消费 → 测试直接报错。宽松模式下这些僵尸 stub 静默存活，测试绿色但已不再验证任何真实行为——**测试与代码的漂移被严格模式拦住**。

### 3. 参数匹配的静默陷阱被堵上

宽松模式下 `when(dao.get(1L)).thenReturn(x)` 而实际传 2L → 静默返回 null → NPE 在别处爆发，排查困难。严格模式当场报 PotentialStubbingProblem，指认精确。

## 边界与代价

- 严格模式对"同一 stub 多用例共享"不友好（如 @BeforeEach 里的公共 stub 部分用例不消费）→ 解法：`lenient().when(...)` 显式声明，或把公共 stub 下沉到各用例
- 代价是 mock 构造代码更多——本块的取舍是接受（错误路径专用 mock 更符合"测试即文档"）

## 一句话

严格模式把"测试写错"从**静默腐化**变成**显式报错**——它是 Mockito 团队对"测试应该精确描述被测行为"这一理念的产品化。
