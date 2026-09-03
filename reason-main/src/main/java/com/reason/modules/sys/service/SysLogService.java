package com.reason.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.sys.entity.SysLogEntity;
import com.reason.modules.sys.form.SysLogForm;


/**
 * 
 *
 * @date 2020-04-22 15:02:49
 */
public interface SysLogService extends IService<SysLogEntity> {

    /**
     * 查询日志信息-分页
     * @param form
     * @return
     */
    PageUtils queryPage(SysLogForm form);

    /**
     * 根据ID 查询日志
     * @param logId
     * @return
     */
    SysLogEntity getInfo(Long logId);
}

