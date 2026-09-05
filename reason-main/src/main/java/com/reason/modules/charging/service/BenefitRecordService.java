package com.reason.modules.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.charging.entity.BenefitRecordEntity;
import com.reason.modules.charging.form.BenefitForm;

/**
 * 免停权益查询服务（管理端只读：跨方凭证全生命周期追踪——签发/核销/过期）
 *
 * @date 2026-09-05
 */
public interface BenefitRecordService extends IService<BenefitRecordEntity> {

    /**
     * 权益分页查询（权益码/车牌模糊 + 状态筛选）
     */
    PageUtils queryPage(BenefitForm form);
}
