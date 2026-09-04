# MyBatis-Plus ServiceImpl 纯单测：@InjectMocks 不注入父类 baseMapper

## 现象

`ServiceImpl<Dao, Entity>` 子类 + 纯 Mockito 单测（无 Spring 上下文）：

- 业务方法调用 `this.save(...)`/`this.getById(...)`/`this.page(...)`/`this.count(...)`（ServiceImpl 内部走父类字段 `baseMapper`）→ 运行抛 `MybatisPlusException: baseMapper can not be null`
- 服务类**有显式 dao 字段**时（`@Autowired ParkSessionDao parkSessionDao` + 同名 @Mock）子类字段注入成功、父类 `baseMapper` 依然 null——`@InjectMocks` 的字段注入只覆盖可唯一/按名匹配的字段，父类泛型字段（擦除后 `BaseMapper`）不在注入目标

## 根因

Mockito `@InjectMocks` 注入链：构造器 → setter → 字段；字段注入按 mock 名/类型匹配目标，`ServiceImpl` 父类的 `baseMapper`（类型擦除为原始 `BaseMapper`，泛型参数丢失）与 mock 的具体 Dao 类型不直接匹配，且类层级继承字段扫描行为依赖注入策略版本。

## 修复模式

业务实现**不用 this.xxx 走 baseMapper，一律显式注入 dao 字段直调**：

```java
@Autowired
private ParkSessionDao parkSessionDao;   // 显式字段（测试 @Mock 同名可注入）

// 代替 this.save(entity)          -> parkSessionDao.insert(entity)
// 代替 this.getById(id)           -> parkSessionDao.selectById(id)
// 代替 this.updateById(entity)    -> parkSessionDao.updateById(entity)
// 代替 this.count(wrapper)        -> parkSessionDao.selectCount(wrapper)
// 代替 this.page(page, wrapper)   -> parkSessionDao.selectPage(page, wrapper)
```

## 边界

- 仅纯单测场景暴露（@InjectMocks 无 Spring）；运行时 Spring 注入 baseMapper 正常——所以**只在测试写红后暴露**，容易误判为"代码没问题"
- parking 域 4 个 ServiceImpl 已统一为 dao 直调风格，与壳子 SysUserServiceImpl 用 `this.page`（其单测不走 ServiceImpl 直接 mock dao 或未测该路径）不同——**新业务 Service 一律 dao 直调**，测试友好且意图更显式

## 经验

纯单测能写出来但 baseMapper 空 → 先怀疑 ServiceImpl 父类方法调用，再怀疑 mock 注入，不要先改测试。
