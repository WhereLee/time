package com.reason.barrier.reporter;

import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.BarrierState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 事件通道路由（批次3；前身=阶段2 DualWriteEventReporter，双写期为当时终态）
 *
 * <p>按 sim.event-channel 三态路由（D-A 定稿）：dual=HTTP+MQ 双写（对照期，两路径共用
 * 同一 traceId 可同号对照）/ mq=仅 MQ（终态：HTTP 事件通道退役为降级开关）/ http=仅 HTTP
 * （降级回滚形态）。开关零代码切换，只作用于事件通道——心跳恒 HTTP（D3：判活不依赖 broker）。</p>
 *
 * <p>多通道独立成败、互不阻塞：任一通道的故障/重试都在各自发送线程内消化（动作线程零感知）。</p>
 *
 * <p>fail-fast：通道配置错位（取值非法 / mq 形态但 sim.mq.enabled=false）在 Bean 构造期
 * 即抛错——不静默降级（与 secret-file 缺失同哲学：配置错误快速暴露）。</p>
 */
@Primary
@Component
public class ChannelRouterEventReporter implements EventReporter {

    private final SimProperties properties;

    private final HttpEventReporter httpReporter;

    /** MQ 通道（条件 Bean：sim.mq.enabled=false 时不存在——ObjectProvider 容忍缺省） */
    private final ObjectProvider<MqEventReporter> mqReporter;

    public ChannelRouterEventReporter(SimProperties properties,
                                      HttpEventReporter httpReporter,
                                      ObjectProvider<MqEventReporter> mqReporter) {
        this.properties = properties;
        this.httpReporter = httpReporter;
        this.mqReporter = mqReporter;
        String channel = properties.getEventChannel();
        //构造期校验（fail-fast）：取值非法、mq 形态但 MQ 通道未启用——配置错位启动即失败
        if (!"mq".equals(channel) && !"http".equals(channel) && !"dual".equals(channel)) {
            throw new IllegalStateException("sim.event-channel 非法取值：" + channel + "（可选 mq/http/dual）");
        }
        if ("mq".equals(channel) && mqReporter.getIfAvailable() == null) {
            throw new IllegalStateException("sim.event-channel=mq 但 sim.mq.enabled=false——事件通道配置错位");
        }
    }

    @Override
    public void report(String deviceNo, BarrierState state, Long commandSeq, String bootId, long eventSeq, String traceId) {
        String channel = properties.getEventChannel();
        if ("mq".equals(channel)) {
            //仅 MQ（终态）：HTTP 事件不发送；构造期已校验 reporter 可用
            mqReporter.getObject().report(deviceNo, state, commandSeq, bootId, eventSeq, traceId);
            return;
        }
        if ("http".equals(channel)) {
            //仅 HTTP（降级回滚形态）
            httpReporter.report(deviceNo, state, commandSeq, bootId, eventSeq, traceId);
            return;
        }
        //dual（对照期）：两通道共用同一 traceId——HTTP header / MQ property 各自承载，平台侧两路径日志可同号对照
        httpReporter.report(deviceNo, state, commandSeq, bootId, eventSeq, traceId);
        mqReporter.ifAvailable(mq -> mq.report(deviceNo, state, commandSeq, bootId, eventSeq, traceId));
    }
}
