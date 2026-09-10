package com.reason.modules.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备通道配置（reason.device.*——0.5 per-device HMAC 后仅剩通道寻址）
 *
 * <p>simBaseUrl：设备模拟服务地址（本地双进程联调；接真实设备时此处即设备协议网关地址）。
 * 鉴权已从共享口令（X-Device-Token）升级为 per-device HMAC（DeviceSignature +
 * DeviceChannelAuthenticator，0.5）：每台设备独立密钥，平台按 X-Device-No 查库验签——
 * 这里不再持有任何共享凭证（仓库零明文）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "reason.device")
public class DeviceChannelProperties {

    /**
     * 设备模拟服务地址（指令下发目标）
     */
    private String simBaseUrl;

    /**
     * 下行指令连接超时毫秒（批次8：通道级超时参数化——设备在"十公里外"，
     * 原硬编码 2000ms，现在可按注入器剧本/网络形态在 yml 标定）
     */
    private int connectTimeoutMillis = 2000;

    /**
     * 下行指令读超时毫秒（批次8：原硬编码 3000ms——发指令不能无限等，
     * 超时是监控任务反馈闭环的前置）
     */
    private int readTimeoutMillis = 3000;

    /**
     * MQ 事件消费配置（阶段2：事件走 RocketMQ 与 HTTP 双写并行——D6；
     * 心跳不走 MQ，留 HTTP 判活——D3）
     */
    private Mq mq = new Mq();

    /**
     * MQ 通道配置（reason.device.mq.*）
     */
    @Data
    public static class Mq {

        /**
         * 消费开关（false=consumer 不启动，回滚纯 HTTP 形态——回滚路径即双写期设计的本意）
         */
        private boolean enabled = true;

        /**
         * RocketMQ proxy gRPC 端点（5.x 客户端走 gRPC；本机联调 127.0.0.1:8081）
         */
        private String endpoint = "127.0.0.1:8081";

        /**
         * 事件 topic（单队列全局有序——D5 保序）
         */
        private String topic = "device-event";

        /**
         * 消费组（broker 侧 retryMaxTimes=3：重投耗尽进死信 %DLQ%platform-device-event）
         */
        private String consumerGroup = "platform-device-event";
    }
}
