package com.reason.modules.charging.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.exception.RRException;
import com.reason.modules.charging.dao.ChargeSessionDao;
import com.reason.modules.charging.entity.ChargeSessionEntity;
import com.reason.modules.charging.enums.ChargeSessionState;
import com.reason.modules.charging.service.ChargeSessionService;
import com.reason.modules.job.task.ITask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 充电会话超时巡检任务（调度注册：schedule_job → chargeSessionTimeoutTask，每 5 分钟）
 *
 * <p>语义（M1-1 定稿）：设备断连/丢失上报的兜底——充电中且开始超阈值（默认 2h，M2 心跳接入后可精确化）
 * 的悬挂会话，逐条走 {@link ChargeSessionService#timeoutFinish}：超时结束 + 0 电 0 元订单 + 不发权益 + 桩释放。</p>
 *
 * <p>幂等与容错：单条失败（如已被 cancel 抢先终态化）只 warn 不中断整批——下轮重扫自然跳过
 * （非充电中态不再命中）；单条条件更新仅一次生效，重复巡检不产生重复订单（u_order_session 兜底）。</p>
 *
 * @date 2026-09-05
 */
@Slf4j
@Component("chargeSessionTimeoutTask")
public class ChargeSessionTimeoutTask implements ITask {

    /** 悬挂判定阈值：充电开始超过该秒数仍未结束（M1 固定 2h，参数化归 M2 设备治理） */
    private static final long TIMEOUT_SECONDS = 2L * 3600;

    @Autowired
    private ChargeSessionDao chargeSessionDao;

    @Autowired
    private ChargeSessionService chargeSessionService;

    @Override
    public void run(String params) {
        long now = System.currentTimeMillis() / 1000;
        long threshold = now - TIMEOUT_SECONDS;
        List<ChargeSessionEntity> hung = chargeSessionDao.selectList(new LambdaQueryWrapper<ChargeSessionEntity>()
                .eq(ChargeSessionEntity::getSessionState, ChargeSessionState.CHARGING.getCode())
                .le(ChargeSessionEntity::getSessionStartTime, threshold));
        if (hung.isEmpty()) {
            log.info("充电会话超时巡检完成：无悬挂会话");
            return;
        }
        int finished = 0;
        for (ChargeSessionEntity session : hung) {
            try {
                chargeSessionService.timeoutFinish(session.getSessionId(), "超时巡检：超过 " + (TIMEOUT_SECONDS / 3600) + " 小时未结束");
                finished++;
            } catch (RRException e) {
                //单条失败不中断整批：会话可能已被并发终态化（cancel/finish 赢），下轮不再命中
                log.warn("超时强制结束未生效（可能已被并发终态化）：sessionId={}, err={}", session.getSessionId(), e.getMessage());
            }
        }
        log.info("充电会话超时巡检完成：命中 {} 条，强制结束 {} 条", hung.size(), finished);
    }
}
