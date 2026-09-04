package com.reason.modules.parking.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.parking.entity.ParkSpaceEntity;
import com.reason.modules.parking.form.ParkSpaceForm;
import com.reason.modules.parking.vo.ParkSpaceVO;

/**
 * 车位台账服务
 *
 * <p>无物理删除：删除=禁用（状态 0 空闲 / 2 禁用 互切）；
 * 占用（1）仅由入场事务产生，管理端不可手动置位。</p>
 *
 * @date 2026-09-05
 */
public interface ParkSpaceService extends IService<ParkSpaceEntity> {

    /**
     * 新增车位（编号唯一预查重 + DB 唯一索引兜底）
     *
     * @param vo             车位参数
     * @param operatorUserId 操作人（sys_user.user_id）
     * @throws com.reason.common.exception.RRException 编号为空/已存在/状态非法
     */
    void saveSpace(ParkSpaceVO vo, Long operatorUserId);

    /**
     * 修改车位（占用中禁止编辑与禁用；编号唯一预查重排除自身 + DB 兜底）
     *
     * @param vo             车位参数（全量更新：编号/区域/状态）
     * @param operatorUserId 操作人（sys_user.user_id）
     * @throws com.reason.common.exception.RRException 车位不存在/占用中/编号已存在/状态非法
     */
    void updateSpace(ParkSpaceVO vo, Long operatorUserId);

    /**
     * 车位分页查询（编号/区域模糊 + 状态精确筛选）
     */
    PageUtils queryPage(ParkSpaceForm form);
}
