package com.reason.modules.parking.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.modules.job.task.ITask;
import com.reason.modules.parking.dao.ParkSessionDao;
import com.reason.modules.parking.entity.ParkSessionEntity;
import com.reason.modules.parking.enums.ParkSessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 长期占用巡检任务（调度注册：schedule_job → parkLongOccupancyTask，每小时）
 *
 * <p>语义：停车会话进行中超过阈值（默认 3 天，运维口径）→ 告警不动账。
 * 只告警是显式取舍：车辆物理在场，系统无权限也不应该凭空释放车位/强制结算——
 * 运营动作（联系车主/人工处理）由人决策，本 job 只负责把异常暴露到日志与执行记录。</p>
 *
 * @date 2026-09-05
 */
@Slf4j
@Component("parkLongOccupancyTask")
public class ParkLongOccupancyTask implements ITask {

    /** 长期占用阈值：入场超过该秒数仍进行中（3 天） */
    private static final long OCCUPANCY_SECONDS = 3L * 24 * 3600;

    @Autowired
    private ParkSessionDao parkSessionDao;

    @Override
    public void run(String params) {
        long now = System.currentTimeMillis() / 1000;
        long threshold = now - OCCUPANCY_SECONDS;
        List<ParkSessionEntity> longOccupied = parkSessionDao.selectList(new LambdaQueryWrapper<ParkSessionEntity>()
                .eq(ParkSessionEntity::getSessionState, ParkSessionState.ONGOING.getCode())
                .le(ParkSessionEntity::getSessionEntryTime, threshold));
        if (longOccupied.isEmpty()) {
            log.info("长期占用巡检完成：无超期占用（{} 天阈值）", OCCUPANCY_SECONDS / 86400);
            return;
        }
        for (ParkSessionEntity session : longOccupied) {
            log.warn("长期占用告警：spaceNo={}, plateNo={}, sessionId={}, 已停 {} 小时",
                    session.getSpaceNo(), session.getPlateNo(), session.getSessionId(),
                    (now - session.getSessionEntryTime()) / 3600);
        }
        log.info("长期占用巡检完成：告警 {} 条（只告警不动账，运营动作人工决策）", longOccupied.size());
    }
}
