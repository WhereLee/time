package com.reason.modules.device.service;

import com.reason.common.utils.PageUtils;
import com.reason.modules.device.form.GateManualOpForm;
import com.reason.modules.sys.entity.SysUserEntity;

/**
 * 闸机人工操作服务（手动抬杆闭环：校验 → 指令下发 → 留痕）
 *
 * @date 2026-09-06
 */
public interface GateManualOpService {

    /**
     * 手动抬杆（管理端操作，走设备通道下发 OPEN_GATE）
     *
     * <p>审计语义：操作人/原因/车牌必录，指令结果无论成败都落留痕表
     * （设备不可达同样留现场）——人工放行不成为"系统里消失"的通道。</p>
     *
     * @param deviceNo 目标闸机设备号（GATE-E-OUT 等）
     * @param plateNo  车牌号（人工录入，可空）
     * @param reason   操作原因（必填）
     * @param operator 操作人（管理端登录用户）
     * @return 指令下发是否成功（false 时留痕 op_result=1，调用方按提示展示）
     */
    boolean liftGate(String deviceNo, String plateNo, String reason, SysUserEntity operator);

    /**
     * 手动抬杆记录分页（设备/车牌/结果筛选，时间倒序）
     */
    PageUtils queryPage(GateManualOpForm form);
}
