# MQ 环境笔记（MQ-ENV-NOTES）

> 阶段 2 交付物（规格 §4-A3/§7）：本机 RocketMQ 环境探测结论、启动方式、踩坑记录
> 日期：2026-09-08 | 探测执行：主 agent（外部 agent 开发前已完成，结论直接采用）

## 1. 环境事实

| 项 | 值 |
|---|---|
| RocketMQ 版本 | 5.3.1（bin 发行版） |
| ROCKETMQ_HOME | `D:\rocketmq-5.3.1\rocketmq-all-5.3.1-bin-release` |
| 架构形态 | **proxy 独立进程**（bin 有独立 mqproxy.cmd，非 broker 内嵌）——完整链路三进程 |
| namesrv | 127.0.0.1:9876 |
| broker remoting | 127.0.0.1:10911（store 默认 `%USERPROFILE%\store`） |
| proxy gRPC | 127.0.0.1:8081（**实测可用**） |
| 内存参数 | 发行版已预调优（runbroker 512m / runserver 256m），无需覆盖 |
| 客户端选型 | **`org.apache.rocketmq:rocketmq-client-java:5.2.2`**（gRPC 协议，Apache 官方 5.x 客户端；client-java 版本线独立于服务端，5.2.2 连 broker 5.3.1 proxy 实测兼容） |

## 2. 一键启动

**双击 `D:\rocketmq-5.3.1\start-rocketmq.bat`** → 三窗口：rmq-namesrv / rmq-broker / rmq-proxy。
停止：关三窗口，或 `bin\mqshutdown.cmd`。

等价手动命令（bin 目录下）：
```
mqnamesrv.cmd
mqbroker.cmd -n 127.0.0.1:9876
mqproxy.cmd -pm cluster -n 127.0.0.1:9876 -pc ..\conf\proxy-dev.json
```

## 3. 踩坑记录（外部 agent 必读）

1. **mqproxy 参数不是 `-c`**：5.3.1 用 `-pc <proxyConfigPath>`，且需显式 `-pm cluster`（连独立 broker）+ `-n <namesrv>`。用 `-c` 直接报 usage。
2. **proxy 配置文件是 JSON 不是 properties**：ProxyStartup 用 fastjson 解析，properties 格式直接 JSONException。配置 `conf/proxy-dev.json`：
   ```json
   { "rocketMQClusterName": "DefaultCluster", "grpcServerPort": 8081, "remotingListenPort": 0 }
   ```
3. **topic 必须显式建**（5.x broker autoCreateTopicEnable 默认关）：
   ```
   mqadmin.cmd updateTopic -n 127.0.0.1:9876 -t device-event -c DefaultCluster -w 1 -r 1
   ```
   单读写队列（-w 1 -r 1）= D5 全局有序前提。**已建**（topicRoute 实测 readQueueNums=1/writeQueueNums=1）。
4. **broker 注册地址是局域网 IP**（实测 192.168.11.61:10911）：producer 经 proxy 转发时连该地址——同网段可用；**换网络后 broker 注册地址失效**，建议 broker 启动加 `brokerIP1=127.0.0.1`（本地联调固定回环）。当前未加（剧本实测同网段通过），列为环境优化项。

## 4. 消费组

- `platform-device-event`（首次订阅自动创建；retryMaxTimes 默认，毒消息路径由消费端 ack 丢弃控制不入重投循环）
- 死信：`%DLQ%platform-device-event`（重投耗尽进）

## 5. 验证记录

- 三端口监听实测（9876/10911/8081）✅
- topic device-event 单队列 ✅
- 端到端剧本（双写/broker 故障/平台重启续消费）见 `阶段2-实施记录.md` §三
