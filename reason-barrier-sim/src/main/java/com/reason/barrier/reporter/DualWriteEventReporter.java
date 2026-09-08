package com.reason.barrier.reporter;

import com.reason.barrier.model.BarrierState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 双写事件上报（阶段2，D6 双写期：HTTP 保留 + MQ 新增并行投递）
 *
 * <p>EventReporter 现有调用点（Barrier 动作完成后）不变——注册表注入的本 Bean 是唯一出口，
 * 对内扇出到两条通道：</p>
 * <ul>
 *   <li>HTTP：永远发送（行为与阶段1 完全一致，双写期不退役——退役判定在阶段3，D6）；</li>
 *   <li>MQ：sim.mq.enabled=true 时 Bean 存在才发送；false 时 Bean 不创建，
 *       自动退化纯 HTTP 回滚形态（回滚开关零代码改动）。</li>
 * </ul>
 * <p>两通道独立成败、互不阻塞：任一通道的故障/重试都在各自发送线程内消化（动作线程零感知）。</p>
 */
@Primary
@Component
public class DualWriteEventReporter implements EventReporter {

    private final HttpEventReporter httpReporter;

    /** MQ 通道（条件 Bean：sim.mq.enabled=false 时不存在——ObjectProvider 容忍缺省） */
    private final ObjectProvider<MqEventReporter> mqReporter;

    public DualWriteEventReporter(HttpEventReporter httpReporter, ObjectProvider<MqEventReporter> mqReporter) {
        this.httpReporter = httpReporter;
        this.mqReporter = mqReporter;
    }

    @Override
    public void report(String deviceNo, BarrierState state, Long commandSeq, String bootId, long eventSeq, String traceId) {
        //两通道共用同一 traceId：HTTP header / MQ property 各自承载，平台侧两路径日志可同号对照
        httpReporter.report(deviceNo, state, commandSeq, bootId, eventSeq, traceId);
        mqReporter.ifAvailable(mq -> mq.report(deviceNo, state, commandSeq, bootId, eventSeq, traceId));
    }
}
