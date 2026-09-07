package com.reason.barrier.network;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网络剧本注入器（应用层——逻辑形态要求：设备链路不可信，故障可制造、本地可控、可进 CI；
 * 见 document/plans/升降杆样例-红队评估与成长升级路线.md §1 注入器承重）
 *
 * <p>0.1 注入最小集：
 * <ul>
 *   <li>blockUpstream：sim→平台 方向断（事件+心跳都上不去）——T20 单向断剧本：设备执行了指令、
 *       平台却收不到任何反馈；也用于事件丢失重试与心跳离线剧本；</li>
 *   <li>blockDownstream：平台→sim 方向断（入站指令被拒，模拟平台连不上设备）——反向单向断剧本；</li>
 *   <li>dropNextEvent：丢下一次事件上报（事件失败重试验证：第一次丢、重试成功）。</li>
 * </ul>
 * 通过 POST /sim/network 运行时切换（不重启进程）。</p>
 */
@Slf4j
@Component
public class NetworkCondition {

    /** sim→平台 方向断（上行事件与心跳均被丢弃） */
    private volatile boolean blockUpstream = false;

    /** 仅事件通道断（0.6 FAULT 心跳收口剧本：事件上不去、心跳正常——双通道独立故障注入） */
    private volatile boolean blockEvents = false;

    /** 平台→sim 方向断（入站 /cmd 与 /cmd/query 全部拒绝） */
    private volatile boolean blockDownstream = false;

    /** 丢下一次事件上报（验证事件失败重试；心跳不消费此开关——心跳周期自带重试语义） */
    private final AtomicBoolean dropNextEvent = new AtomicBoolean(false);

    /**
     * 心跳单次人为延迟毫秒（P5/T11 剧本：网络故障延迟 0.5-2s+ 时单台上报阻塞——
     * 并行化后只拖慢自己的轮次，其余设备节拍不受影响；模拟器运行时注入）
     */
    private volatile long heartbeatDelayMillis = 0;

    public boolean isBlockUpstream() {
        return blockUpstream;
    }

    public boolean isBlockDownstream() {
        return blockDownstream;
    }

    /**
     * 事件上报是否应被丢弃（方向断/事件独立断优先；否则消费一次"单次丢包"）
     */
    public boolean shouldDropEvent() {
        return blockUpstream || blockEvents || dropNextEvent.compareAndSet(true, false);
    }

    /**
     * 心跳是否应被丢弃（只跟方向断）
     */
    public boolean shouldDropHeartbeat() {
        return blockUpstream;
    }

    public void setBlockUpstream(boolean value) {
        this.blockUpstream = value;
        log.warn("[网络剧本] 上行阻断(事件+心跳 -> 平台) 置为 {}", value);
    }

    public void setBlockEvents(boolean value) {
        this.blockEvents = value;
        log.warn("[网络剧本] 事件通道独立阻断(仅事件 -> 平台) 置为 {}", value);
    }

    public void setBlockDownstream(boolean value) {
        this.blockDownstream = value;
        log.warn("[网络剧本] 下行阻断(平台 -> 指令) 置为 {}", value);
    }

    public void setDropNextEvent(boolean value) {
        if (value) {
            dropNextEvent.set(true);
            log.warn("[网络剧本] 已布防：下一次事件上报将被丢弃（验证上报失败重试）");
        }
    }

    public long getHeartbeatDelayMillis() {
        return heartbeatDelayMillis;
    }

    public void setHeartbeatDelayMillis(long millis) {
        this.heartbeatDelayMillis = millis;
        log.warn("[网络剧本] 心跳延迟注入置为 {}ms（P5：验证慢设备不拖垮整组节拍）", millis);
    }
}
