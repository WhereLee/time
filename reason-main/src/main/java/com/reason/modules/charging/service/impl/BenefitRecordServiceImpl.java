package com.reason.modules.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.utils.Constant;
import com.reason.common.utils.MapUtils;
import com.reason.common.utils.PageUtils;
import com.reason.common.utils.Query;
import com.reason.modules.charging.dao.BenefitRecordDao;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.form.BenefitForm;
import com.reason.modules.charging.service.BenefitRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 免停权益查询服务实现
 *
 * @date 2026-09-05
 */
@Service("benefitRecordService")
public class BenefitRecordServiceImpl extends ServiceImpl<BenefitRecordDao, BenefitRecordEntity>
        implements BenefitRecordService {

    @Autowired
    private BenefitRecordDao benefitRecordDao;

    @Override
    public PageUtils queryPage(BenefitForm form) {
        IPage<BenefitRecordEntity> page = new Query<BenefitRecordEntity>().getPage(new MapUtils()
                .put(Constant.PAGE, form.getPage()).put(Constant.LIMIT, form.getLimit()));
        benefitRecordDao.selectPage(page, new LambdaQueryWrapper<BenefitRecordEntity>()
                .like(org.springframework.util.StringUtils.hasText(form.getBenefitNo()),
                        BenefitRecordEntity::getBenefitNo, form.getBenefitNo())
                .like(org.springframework.util.StringUtils.hasText(form.getPlateNo()),
                        BenefitRecordEntity::getPlateNo, form.getPlateNo())
                .eq(form.getBenefitState() != null, BenefitRecordEntity::getBenefitState, form.getBenefitState())
                .orderByDesc(BenefitRecordEntity::getBenefitId));
        return new PageUtils(page);
    }
}
