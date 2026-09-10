# 坑：Boot 3.4+ 下 springdoc 2.3.0 扫描 @RestControllerAdvice 触发 NoSuchMethodError（/v3/api-docs 500）

> 状态：已踩·已修（2026-09-10，A1 双端 Spring Boot 对齐时启动冒烟暴露）
> 适用：knife4j 4.5.0（官方最新）或任何内置 springdoc < 2.7 的 OpenAPI 文档壳，配 Spring Boot 3.4+

## Bug 类别

文档工具链的二进制不兼容——编译期通过、运行期首次请求文档端点时炸（Linkage Error，Error 非 Exception，业务异常处理器接不住）。

## 现象

Boot 3.2.12 → 3.5.16 升级后，应用启动成功、业务接口正常，但：

- `GET /api/v3/api-docs` 返回统一 Result 包装的 `{"code":500,...}`（真实异常被全局处理器吞掉一层）
- `doc.html` 页面白屏（前端拉不到分组文档）
- 平台 info.log 里可见真身：

```
java.lang.NoSuchMethodError: 'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
    at org.springdoc.core.service.GenericResponseService...
```

## 根因

- knife4j 4.5.0（官方最新版，2024-01）内置 **springdoc 2.3.0**；
- springdoc 的 `GenericResponseService.getGenericMapResponse` 在**扫描 `@RestControllerAdvice` 类**时调用单参构造器 `new ControllerAdviceBean(bean)`；
- Spring Framework 6.2（Boot 3.4 起）**移除了该单参构造器**（改为三参 `(String beanName, BeanFactory, ControllerAdvice)`）→ 运行时找不到方法。

即：不是我们的代码错，是"弹簧升级了、文档壳里焊死的旧螺丝拧不上了"。

## 修复模式

给 `@RestControllerAdvice` 类加 `@Hidden`（`io.swagger.v3.oas.annotations.Hidden`），让 springdoc 跳过对该类的扫描与包装，不再触发那段旧构造器调用：

```java
@Slf4j
@RestControllerAdvice
@Hidden // 让 springdoc 跳过本类；异常处理器本无需进 API 文档，零功能损失
public class RRExceptionHandler { ... }
```

实测：加注解后 `/v3/api-docs` 恢复 200，文档 JSON 完整（50KB，含全部接口路径）。

## 为什么不动依赖版本

- knife4j **官方最新即 4.5.0**，仓库无更新版本可升（上游迟迟未适配 springdoc 2.7+）；
- 强升 springdoc 到 2.7/2.8 属"未经上游适配的组合"——knife4j 内部扩展点与 springdoc 2.7+ 的 API 交互不可控，可能炸出新问题；
- `@Hidden` 只影响文档扫描面，是规避触发路径的最小改动，社区（Boot 3.4/3.5 + knife4j 4.5.0）标准解法。

## 教训

1. **升级 Boot 后必须专测文档端点**（`/v3/api-docs`、`doc.html`），"启动成功 + 业务接口 200"不代表全绿——本坑是惰性触发（首次请求文档才炸）。
2. Boot 3.4+ 环境里，任何内置 springdoc < 2.7 的文档壳都要先过一遍 `ControllerAdviceBean` 兼容检查。
3. 症状被全局异常处理器包装成业务 500 时，去 info/error 日志找 root cause——`NoSuchMethodError` 真身只在日志里。
4. 上游未适配时，修型优先级：**规避触发路径（@Hidden）> 强升传递依赖**。
