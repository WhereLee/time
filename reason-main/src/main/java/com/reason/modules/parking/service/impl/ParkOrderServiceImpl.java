package com.reason.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.utils.Constant;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.parking.dao.ParkOrderDao;
import com.reason.modules.parking.entity.ParkOrderEntity;
import com.reason.modules.parking.form.ParkOrderForm;
import com.reason.modules.parking.service.ParkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 停车订单服务实现
 *
 * @date 2026-09-05
 */
@Service("parkOrderService")
public class ParkOrderServiceImpl extends ServiceImpl<ParkOrderDao, ParkOrderEntity>
        implements ParkOrderService {

    @Autowired
    private ParkOrderDao parkOrderDao;

    @Override
    public PageUtils queryPage(ParkOrderForm form) {
        IPage<ParkOrderEntity> page = new Query<ParkOrderEntity>().getPage(new MapUtils()
                        .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        parkOrderDao.selectPage(page, new LambdaQueryWrapper<ParkOrderEntity>()
                        .like(StringUtils.hasText(form.getSpaceNo()), ParkOrderEntity::getSpaceNo, form.getSpaceNo())
                        .like(StringUtils.hasText(form.getPlateNo()), ParkOrderEntity::getPlateNo, form.getPlateNo())
                        .orderByDesc(ParkOrderEntity::getOrderId));
        return new PageUtils(page);
    }
}
