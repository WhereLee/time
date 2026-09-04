# 待办：@Async 场景的 MDC traceId 传递（TaskDecorator）

> 状态：待触发
> 登记时间：2026-09-04（可观测性块 C1）
> 触发条件：代码中出现第一个 @Async / 自定义线程池执行点

## 背景

TraceIdFilter 已实现同步链路 traceId（MDC），但 MDC 是线程本地变量：
**异步线程（@Async/线程池）拿不到请求线程的 MDC**，异步段的日志 traceId 为空，链路断裂。

## 方案（触发时执行）

- 为 TaskExecutor 配置 `TaskDecorator`：提交任务时快照当前 MDC 上下文，任务执行前 restore、finally 清理——ThreadPoolTaskExecutor.setTaskDecorator 或 @Async 场景的 AsyncConfigurer 自定义 executor
- 备选（消息/定时场景）：目标线程自行生成新 traceId 并标记父链关系

## 影响

- 未来若引入 RocketMQ 消费线程或异步补偿任务，消费日志的 traceId 归属必须先决策（继承 or 新链）
