package com.reason.modules.parking.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.parking.entity.ParkOrderEntity;
import com.reason.modules.parking.form.ParkOrderForm;

/**
 * 停车订单服务（订单只增不改：出场事务生成后永不变更）
 *
 * @date 2026-09-05
 */
public interface ParkOrderService extends IService<ParkOrderEntity> {

    /**
     * 订单分页查询（管理端只读：车牌/车位模糊；按生成时间倒序）
     */
    PageUtils queryPage(ParkOrderForm form);
}
