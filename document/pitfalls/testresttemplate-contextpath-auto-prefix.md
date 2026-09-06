# 坑：TestRestTemplate 自动拼接 context-path 导致双层 /api（401 假绿）

> 状态：已踩·已修（2026-09-04，A2 块 CI 首轮失败——GitHub Actions 实跑暴露）
> 适用：@SpringBootTest(webEnvironment=RANDOM_PORT) + context-path 非空的应用

## Bug 类别

测试客户端路径与服务器 context-path 重复拼接——期望 200 的接口收到 401。

## 现象

CI 中 AuthIT 两个用例失败：

```
ResourceAccessException: I/O error on POST request for "http://localhost:38991/api/api/sys/login":
cannot retry due to server authentication, in streaming mode
```

错误 URL 里是**双层 `/api/api`**。服务器返回 401（该路径不在 Security 放行列表），
RestTemplate 在流式读取状态收到认证挑战无法重试 → ResourceAccessException。

## 根因

`RANDOM_PORT` 下自动注入的 `TestRestTemplate` 内置 `LocalHostUriTemplateHandler`——
它继承 `ServletUriTemplateHandler`，**默认 includeContextPath=true**，会自动把应用的
context-path（/api）拼到请求路径前。用例路径又写了完整 `/api/sys/login` → 双层。

## 隐蔽性：为什么一半用例"碰巧通过"

无 token / 伪造 token 两个用例**期望的就是 401**——双层路径同样返回 401，断言通过。
但它们实际验证的已不是"受保护接口被正确拦截"，而是"任意不存在路径都返回 401"——
**被测对象是错的，测试却绿了**。断言方向与路径 bug 同向时，测试会骗人。

## 修复模式

用例路径**不带 context-path 前缀**（template 自动拼）：

```java
// 错误：/api/sys/login
// 正确：/sys/login
```

## 教训

1. **失败先看错误里的 URL 本身**——路径拼接问题在 URL 上直接现形（本坑 30 秒可定位，
   GitHub 端 AI 的 sleep/健康检查/超时建议全是方向性噪音——没有连接失败，是路径错）。
2. **"碰巧通过"的用例是假绿**：当断言方向（期望 401/404/500）与 bug 效果同向时，
   测试对被测代码零验证却显示绿色——排查 CI 失败时要警惕"为什么只有部分用例失败"，
   通过的用例可能正在用错误路径验证错误行为。
3. 修复后必须让两个原失败用例真实走通业务链路（登录发 token → 带 token 访问），
   假绿用例在路径修复后验证的对象才回归真实。
