# 坑：@Value 无法绑定 yaml 列表形式（数组风格展开为索引属性）

> 状态：已踩·已修（2026-09-04，B4 豁免 URI 配置化，启动回归暴露）
> 适用：@Value 注入集合类型配置时

## Bug 类别

配置文件格式与注入方式不匹配——应用启动即失败（bean 注入异常）。

## 现象

将豁免 URI 硬编码改为 yaml **列表**后启动失败：

```
Error creating bean with name 'authServiceImpl': Injection of autowired dependencies failed
```

（@Value placeholder 解析失败 → bean 注入 UnsatisfiedDependency 链。）

## 根因

yaml 的列表形式会被 Spring 展开为**索引属性**：

```yaml
reason.security.change-password-exempt-uris:
  - /api/a
```
→ 属性源中实际存在的是 `...exempt-uris[0]=/api/a`，**没有标量的 `...exempt-uris` 属性** → `@Value("${reason.security.change-password-exempt-uris}")` 找不到 → 启动失败。

## 修复模式

yaml 用**逗号分隔的标量字符串**（Spring @Value 对 List 注入自动按逗号拆分 + trim）：

```yaml
change-password-exempt-uris: /api/a, /api/b, /api/c
```

```java
@Value("${reason.security.change-password-exempt-uris}")
private List<String> changePasswordExemptUris;   // 自动拆分
```

## 教训

1. **@Value 吃标量，yaml 列表是给 @ConfigurationProperties 吃的**——集合配置二选一：
   yaml 列表 + 绑定类（类型安全），或逗号标量 + @Value（轻量）；混用即踩本坑。
2. 启动期注入失败的症状在整条 bean 链的上游冒头（报 authTokenFilter 而真凶在 authServiceImpl 的字段注入），
   排查从 "Injection of autowired dependencies failed" 处剥到底层 caused by。
