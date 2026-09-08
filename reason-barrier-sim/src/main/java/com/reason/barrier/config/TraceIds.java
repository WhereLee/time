package com.reason.barrier.config;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * traceId 工具（批次1 可观测性地基，D-E）——与平台 TraceIdFilter 同算法同常量
 *
 * <p>贯穿链路：平台下发指令携 X-Trace-Id → sim 沿用（无则生成）→ 动作线程 → 事件上报
 * （HTTP header / MQ property）→ 平台消费侧塞 MDC。一次抬杆从"管理端点击"到"台账销账"
 * 同一 traceId 可串起，事故定位到报文级。</p>
 *
 * <p>设备自发事件（外力改态/人工复位/心跳）无上游 traceId，由 sim 生成——链路起点在设备侧。</p>
 */
public final class TraceIds {

    /** HTTP 承载头（与平台 TraceIdFilter.TRACE_ID_HEADER 同值——契约一致性） */
    public static final String HEADER = "X-Trace-Id";

    /** MDC key（logback pattern %X{traceId} 取此） */
    public static final String MDC_KEY = "traceId";

    /** MQ message property key（事件走 MQ 时的 traceId 承载——契约 §7.1） */
    public static final String MQ_PROPERTY = "traceId";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    /**
     * 生成 32 位 hex traceId（16 字节 SecureRandom，与平台同源策略）
     */
    public static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /**
     * 沿用或生成：入站携带则沿用（平台下发链路贯穿），否则新生成（设备自发事件）
     */
    public static String orGenerate(String incoming) {
        return (incoming == null || incoming.isBlank()) ? generate() : incoming;
    }

    private TraceIds() {
    }
}
