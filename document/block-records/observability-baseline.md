# 块记录：可观测性地板（traceId 链路 / actuator 最小暴露 / 慢 SQL 确认）

> 块循环：2026-09-04
> 验证：mvn clean test 32 用例全绿 + 启动回归（响应头 X-Trace-Id 与日志 traceId 一致、health UP、metrics 关闭）

## C1 traceId（MDC 链路串联）

实现四件：
1. `TraceIdFilter`（common/filter，OncePerRequestFilter）：请求头 `X-Trace-Id` 透传复用 → 无则 16 字节 SecureRandom → 32 hex；响应头回写；**finally MDC.remove**（Tomcat 线程复用防串号，与 SecurityContext 清理同构）
2. FilterConfig 注册：order = HIGHEST_PRECEDENCE + 1（紧跟 RequestContextFilter，早于 Security 链 -100 与 xssFilter）
3. logback：`LOG_PATTERN_FORMAT` 加 `[%X{traceId}]`（四个 appender 共用一个 pattern 变量，一处生效）
4. 单测 5 用例：自生成/透传复用/响应头回写/**异常路径 finally 清理**/生成器随机性

**回归证据**：登录响应头 `X-Trace-Id: 836872b46a4a398ca964ec3cd8995eca` ↔ 同请求 8 行业务日志全部携带同一 traceId——闭环成立。

**范围决策**：@Async/自定义线程池零使用 → TaskDecorator 不做，登记 roadmap（触发条件：第一个异步场景出现时，否则 MDC 在新线程丢失）；Quartz job 线程天然无请求 traceId，不强行关联（业务 job 阶段再议）。

## C2 actuator 最小暴露

- pom 加 spring-boot-starter-actuator；yml：`management.endpoints.web.exposure.include: health,info`
- **插曲：actuator 路径被 Security 拦截（401）**——health/info 加入 SecurityConfig ANON_PATHS（探活端点匿名访问是常规操作，show-details 默认 never 不泄漏组件细节）
- 回归：`/api/actuator/health` → `{"status":"UP"}`；`/api/actuator/metrics` → 404（未暴露 ✅）

## C3 Druid 慢 SQL 确认（纯文档）

- 三个环境 yml（dev/prod/test）均已配 `log-slow-sql: true + slow-sql-millis: 1000`——零代码
- 排障素材记录：慢 SQL 输出到 logger `druid.sql.SlowSql`（INFO 级），超过 1 秒的 SQL 会带 "slow sql" 关键字出现在日志
