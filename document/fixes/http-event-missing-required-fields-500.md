# 修复：HTTP 事件入口缺必填字段拆箱 NPE 出 500（契约 §5 语义失守）

> 状态：已修（2026-09-11，批次8 独立质量评估发现）
> 来源：封版后代码通读——双入口（HTTP/MQ）校验不对称

## 缺陷描述

协议 v2 §3.1 规定事件必填 `deviceNo/state/bootId/eventSeq`（`commandSeq` 可空），§5 要求协议垃圾
返回 400。实际实现：

- MQ 消费路径（`DeviceEventMqConsumer.process`）有必填校验 → 缺字段=毒消息 ACK 丢弃；
- HTTP 路径（`DeviceEventController.report`）只校验状态码合法性，**未校验 bootId/eventSeq**，
  直接进入 `DeviceEventServiceImpl.handleStateEvent`；
- 服务接口 `updateStateByEventWithSeq(..., long eventSeq)` 是基本类型——`eventSeq=null` 时
  自动拆箱 NPE → 全局异常处理 500。

结果：同一份协议垃圾，走 MQ 是"毒消息丢弃"，走 HTTP 是 500——错误语义双端不一致，且 500
掩盖调用方协议错误（设备侧自省窗口失明）。

## 缺陷性质

**双入口校验不对称 + 基本类型签名隐藏了可空性**——接口把"必填"写成 `long`，但传输层的
反序列化对象是可空 `Long`，校验责任落空。

## 修复

在编排入口单点收口（HTTP 与 MQ 共用同一服务，一处生效双入口获益）：

```java
// DeviceEventServiceImpl.handleStateEvent 首部
if (form.getDeviceNo() == null || form.getDeviceNo().isEmpty()
        || form.getState() == null
        || form.getBootId() == null || form.getBootId().isEmpty()
        || form.getEventSeq() == null) {
    throw new RRException("协议v2事件缺必填字段(deviceNo/state/bootId/eventSeq)");
}
```

- HTTP：既有 `catch (RRException) → ResponseStatusException(400)` 自动生效；
- MQ：既有 `catch (RRException) → ACK 毒消息` 自动生效（与既有校验双重保险）。

## 回归验证

`DeviceEventServiceImplTest` 新增 3 例（缺 eventSeq / 空 bootId / 缺 state）：
断言抛 `RRException` 且 `verifyNoInteractions` 台账/流水/告警服务——修复前第一例会以 NPE 炸出。

## 关联

- contracts/PROTOCOL-V2.md §3.1（必填字段）、§5（协议垃圾 400）
- document/plans/批次8-五项修复-方案.md 方案 1
