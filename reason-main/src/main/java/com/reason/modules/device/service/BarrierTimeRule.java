package com.reason.modules.device.service;

import com.reason.modules.device.config.BarrierProperties;
import com.reason.modules.device.enums.DeviceState;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 升降杆时间规则（"规则=判断者"对象——纯判断，不碰设备、不碰库）
 *
 * <p>职责只有一条：给定时刻，回答"杆应然是什么状态"（窗口内=升起放行，窗口外=降下禁行）。
 * 它不知道杆现在是什么状态（那是台账/设备的事），也不知道谁在问（自动任务/管理端展示）——
 * 规则与执行彻底解耦，是"对象各管一段"的样板。</p>
 *
 * <p>边界处理：
 * <ul>
 *   <li>整点过渡：半开区间 [open, close)——open 时刻整点即应升，close 时刻整点即应降，
 *       不存在"整点刹那两个答案"的模糊态；</li>
 *   <li>跨天窗口：open &gt; close（如 22:00-06:00 夜间放行）按跨午夜语义判断；</li>
 *   <li>时钟漂移：规则只回答"此刻应然"，漂移导致的偏差由周期对账在下一轮自然吸收
 *       （对账不信任任何一次触发的结果，只信"当前应然 vs 当前实然"）；</li>
 *   <li>规则变更：改配置重启后下一轮对账即按新规则校正——变更被吸收，无需迁移动作。</li>
 * </ul></p>
 */
@Slf4j
@Component
public class BarrierTimeRule {

    private final BarrierProperties properties;

    public BarrierTimeRule(BarrierProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动即校验配置（fail-fast：非法时间格式在启动时爆炸，不留到第一次任务执行才发现）
     */
    @PostConstruct
    public void validate() {
        LocalTime open = parseOpen();
        LocalTime close = parseClose();
        log.info("升降杆时间规则加载：放行窗口 {} -> {}（{}）", open, close,
                open.isBefore(close) ? "当日窗口" : "跨午夜窗口");
    }

    /**
     * 给定时刻的应然状态
     *
     * @param now 当前时刻（调用方传入——规则不自己看钟，可测试性：单测可注入任意时刻）
     * @return DeviceState.UP（窗口内放行）或 DeviceState.DOWN（窗口外禁行）的状态码
     */
    public int desiredState(LocalTime now) {
        LocalTime open = parseOpen();
        LocalTime close = parseClose();
        boolean inWindow;
        if (open.isBefore(close)) {
            //当日窗口 [open, close)：如 08:00-18:00
            inWindow = !now.isBefore(open) && now.isBefore(close);
        } else {
            //跨午夜窗口：如 22:00-06:00，now>=open 或 now<close 均在窗口内
            inWindow = !now.isBefore(open) || now.isBefore(close);
        }
        return inWindow ? DeviceState.UP.getCode() : DeviceState.DOWN.getCode();
    }

    private LocalTime parseOpen() {
        return LocalTime.parse(properties.getAutoOpenTime());
    }

    private LocalTime parseClose() {
        return LocalTime.parse(properties.getAutoCloseTime());
    }
}
