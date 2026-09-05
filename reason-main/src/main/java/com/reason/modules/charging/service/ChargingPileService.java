package com.reason.modules.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.charging.entity.ChargingPileEntity;
import com.reason.modules.charging.form.ChargingPileForm;
import com.reason.modules.charging.form.ChargingPileVO;

/**
 * 充电桩台账服务（无物理删除：停用=删除语义）
 *
 * <p>绑定规则：桩必须绑到存在、未停用、无进行中停车会话的车位（跨上下文能力校验，
 * 不直连 park_space 表）；充电中（state=1）禁止任何编辑（含停用/改绑）——充电中状态仅由
 * 充电会话事务产生，管理端不可手动置位。</p>
 *
 * @date 2026-09-05
 */
public interface ChargingPileService extends IService<ChargingPileEntity> {

    /**
     * 新增桩（编号唯一预查重 + DB 唯一索引兜底；绑车位须存在/未停用/空闲）
     *
     * @param vo             桩参数
     * @param operatorUserId 操作人（sys_user.user_id）
     * @throws com.reason.common.exception.RRException 编号为空/已存在/车位不存在或不可绑
     */
    void savePile(ChargingPileVO vo, Long operatorUserId);

    /**
     * 修改桩（充电中禁编辑；改绑车位同新增规则；编号唯一预查重排除自身 + DB 兜底）
     *
     * @param vo             桩参数（全量更新：编号/车位/状态）
     * @param operatorUserId 操作人（sys_user.user_id）
     * @throws com.reason.common.exception.RRException 桩不存在/充电中/编号已存在/车位不存在或不可绑/状态非法
     */
    void updatePile(ChargingPileVO vo, Long operatorUserId);

    /**
     * 桩分页查询（桩编号模糊 + 状态精确筛选）
     */
    PageUtils queryPage(ChargingPileForm form);
}
