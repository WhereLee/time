package com.reason.modules.device.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 升降杆业务配置（reason.barrier.*）
 *
 * <p>样例尺度：规则/阈值走 yml（生产形态为规则表+管理界面，变更后周期对账自然吸收——
 * 这正是"规则变更被校正吸收"边界的处理方式：改配置不需要任何迁移动作，
 * 下一轮 barrierAutoTask 按新规则比对即生效）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "reason.barrier")
public class BarrierProperties {

    /**
     * 自动升降总开关（false=只手动，演示"纯人工接管"形态）
     */
    private boolean autoEnabled = true;

    /**
     * 白天放行开始时刻（该时刻起应然=升起），HH:mm；支持跨天窗口（open>close 表示跨午夜）
     */
    private String autoOpenTime = "08:00";

    /**
     * 白天放行结束时刻（该时刻起应然=降下），HH:mm
     */
    private String autoCloseTime = "18:00";

    /**
     * 心跳超时秒数（Redis online key 的 TTL；超过无心跳 = 离线）
     */
    private int heartbeatTimeoutSeconds = 30;

    /**
     * 指令到位超时秒数（下发后多久没等到目标状态事件 = 超时，触发重试）
     */
    private int commandTimeoutSeconds = 60;

    /**
     * 指令重试上限（超限停止重试并告警——"重试幂等分层上限"边界：失败显式化，不悄悄无限重试）
     */
    private int maxRetry = 3;

    /**
     * 手动保持期分钟数（手动指令后自动规则让位的窗口——"手动 vs 自动打架"边界）
     */
    private int manualHoldMinutes = 30;

    /**
     * 心跳对账让路窗口秒数：台账在窗口内刚被事件通道更新过，则本次心跳的不一致
     * 大概率是两通道时序差（心跳采样在前、事件写入在后）而非真漂移——本轮跳过校正。
     * 真漂移不会自愈，下轮心跳（间隔后）必被抓住；时序差会自愈，让路避免误报与台账回退
     */
    private int reconcileGraceSeconds = 3;

    /**
     * 同类告警去重窗口秒数（持续异常不刷屏：窗口内同设备同类型只落一条）
     */
    private int alarmDedupSeconds = 300;
}
