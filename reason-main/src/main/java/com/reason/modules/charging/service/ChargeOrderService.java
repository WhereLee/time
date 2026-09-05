package com.reason.modules.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.charging.entity.ChargeOrderEntity;
import com.reason.modules.charging.form.ChargeOrderForm;

/**
 * 充电订单查询服务（管理端只读：订单由结算事务生成后不可变更）
 *
 * @date 2026-09-05
 */
public interface ChargeOrderService extends IService<ChargeOrderEntity> {

    /**
     * 订单分页查询（桩编号/车牌模糊；金额单位为分，展示换算由前端负责）
     */
    PageUtils queryPage(ChargeOrderForm form);
}
