package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.utils.Constant;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.charging.dao.ChargeOrderDao;
import com.reason.modules.charging.entity.ChargeOrderEntity;
import com.reason.modules.charging.form.ChargeOrderForm;
import com.reason.modules.charging.service.ChargeOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 充电订单查询服务实现
 *
 * @date 2026-09-05
 */
@Service("chargeOrderService")
public class ChargeOrderServiceImpl extends ServiceImpl<ChargeOrderDao, ChargeOrderEntity>
        implements ChargeOrderService {

    @Autowired
    private ChargeOrderDao chargeOrderDao;

    @Override
    public PageUtils queryPage(ChargeOrderForm form) {
        IPage<ChargeOrderEntity> page = new Query<ChargeOrderEntity>().getPage(new MapUtils()
                .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        chargeOrderDao.selectPage(page, new LambdaQueryWrapper<ChargeOrderEntity>()
                .like(org.springframework.util.StringUtils.hasText(form.getPileNo()),
                        ChargeOrderEntity::getPileNo, form.getPileNo())
                .like(org.springframework.util.StringUtils.hasText(form.getPlateNo()),
                        ChargeOrderEntity::getPlateNo, form.getPlateNo())
                .orderByDesc(ChargeOrderEntity::getOrderId));
        return new PageUtils(page);
    }
}
