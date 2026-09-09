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
 * 批次2 B8 注入器补全（附录 A 必做档缺口，全部只作用于 HTTP 事件通道）：
 * <ul>
 *   <li>eventDelayMillis：事件发送前人为延迟（持续生效，0 关）——打心跳对账 grace 边界；</li>
 *   <li>replayNextEvent：下一条<strong>成功送达</strong>的事件同报文重发 1 次（一次性，
 *       失败不消费布防——重放剧本验证的是平台幂等吸收，需要"真送达后再重发"）；</li>
 *   <li>reorderNextDeviceNo：该设备下两条事件集齐后按 eventSeq 降序发送（一次性；
 *       只来 1 条超时 5s 原序放行）——乱序剧本验证平台序守卫拒旧序。</li>
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

    /**
     * 事件发送前人为延迟毫秒（批次2 B8，持续生效 0 关）：打心跳对账 grace 边界——
     * 延迟 >grace 时心跳自述先达触发一次 STATE_MISMATCH（已标定行为），事件随后仍被序守卫受理
     */
    private volatile long eventDelayMillis = 0;

    /**
     * 重放布防（批次2 B8，一次性）：下一条<strong>成功送达</strong>的事件同报文重发 1 次——
     * 布防与消费分离（消费点=发送成功之后）：发送失败不消费布防（不放大故障，
     * 重放剧本验证的是平台幂等吸收，需要"真送达后再重发"）
     */
    private final AtomicBoolean replayNextEvent = new AtomicBoolean(false);

    /**
     * 乱序布防（批次2 B8，一次性）：该设备下两条事件集齐后按 eventSeq 降序发送——
     * 乱序跨设备无业务意义（序守卫 per-device），窗口固定 2 条（实现最小、剧本可控）；
     * 只来 1 条超时 5s 原序放行（防"布防后只发生一次动作"剧本卡死通道）
     */
    private volatile String reorderNextDeviceNo = null;

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

    public long getEventDelayMillis() {
        return eventDelayMillis;
    }

    public void setEventDelayMillis(long millis) {
        this.eventDelayMillis = millis;
        log.warn("[网络剧本] 事件延迟注入置为 {}ms（批次2：打心跳对账 grace 边界）", millis);
    }

    public void setReplayNextEvent(boolean value) {
        if (value) {
            replayNextEvent.set(true);
            log.warn("[网络剧本] 已布防：下一条成功送达的事件将同报文重发 1 次（批次2：平台幂等吸收验证）");
        }
    }

    /**
     * 重放布防消费（CAS 取删）：true=本次发送成功后需同报文重发 1 次；调用点=发送成功之后，
     * 失败路径不调用（布防不消费，不放大故障）
     */
    public boolean consumeReplay() {
        return replayNextEvent.compareAndSet(true, false);
    }

    public String getReorderNextDeviceNo() {
        return reorderNextDeviceNo;
    }

    public void setReorderNextDeviceNo(String deviceNo) {
        this.reorderNextDeviceNo = deviceNo;
        log.warn("[网络剧本] 乱序布防 deviceNo={}（该设备下两条事件集齐后按 eventSeq 降序发送，批次2）", deviceNo);
    }

    /**
     * 乱序窗口完成复位（一次性开关随窗口完成自动关闭）
     */
    public void clearReorder() {
        this.reorderNextDeviceNo = null;
    }
}
