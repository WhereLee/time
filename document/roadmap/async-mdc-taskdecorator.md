# 待办：@Async 场景的 MDC traceId 传递（TaskDecorator）

> 状态：部分已解决（MQ 消费线程场景，2026-09 批次1）·剩余待触发（@Async / 自定义线程池执行点）
> 登记时间：2026-09-04（可观测性块 C1）；更新：2026-09-10（A3 roadmap 收口——触发条件部分发生并已解决）
> 触发条件（剩余）：代码中出现第一个 **@Async / 业务自定义线程池**的执行点

## 背景

TraceIdFilter 已实现同步链路 traceId（MDC），但 MDC 是线程本地变量：
**异步线程（@Async/线程池）拿不到请求线程的 MDC**，异步段的日志 traceId 为空，链路断裂。

## 已解决：RocketMQ 消费线程的 traceId 归属（批次1 落地）

原登记的两个潜在触发源之一是"引入 RocketMQ 消费线程"——该场景已发生并已解决：

- **决策（D-E）**：消费侧 traceId **继承**（非新链）——producer（sim）每事件生成链路号写入 message property，
  `DeviceEventMqConsumer` 消费时从 property 取出置 MDC，消费日志与设备侧上报日志可 grep 串联；
- **实证**：批次1 block-record——一次手动抬杆的 traceId 串起 sim/平台两侧 20 条日志；"事件通道中断 5 分钟"
  剧本凭 traceId 定位到报文级；
- **方案差异说明**：TaskDecorator 快照 MDC 适用于"线程池内任务继承提交方上下文"；消息场景的等价物是
  "producer 显式把上下文写入消息属性、consumer 读出重建"——同为继承语义，承载介质不同（property vs 闭包快照）。

## 剩余方案（触发时执行）

- 为业务自定义线程池配置 `TaskDecorator`：提交任务时快照当前 MDC，任务执行前 restore、finally 清理
  （ThreadPoolTaskExecutor.setTaskDecorator / AsyncConfigurer 自定义 executor）；
- Quartz 任务线程现状：每轮任务入口自生成 traceId 并置 MDC、finally 清理（`BarrierMonitorTask.run` 已实现）——
  属"新链"形态，已有既定模式，无需 TaskDecorator。

## 影响

- 消息消费线程的 traceId 归属**已决策**（继承，经 message property）——本待办不再覆盖该场景；
- 未来若引入异步补偿任务/业务线程池，按剩余方案执行；新引入的消息主题沿用 property 承载模式。
