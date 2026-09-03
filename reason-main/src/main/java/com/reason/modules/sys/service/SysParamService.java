package com.reason.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.sys.entity.SysParamEntity;
import com.reason.modules.sys.form.SysParamForm;
import com.reason.modules.sys.vo.SysParamVO;


/**
 * 
 *
 * @date 2020-04-29 10:10:57
 */
public interface SysParamService extends IService<SysParamEntity> {

    /**
     * 查询参数列表-分页
     * @param form
     * @return
     */
    PageUtils queryPage(SysParamForm form);

    /**
     * 新增参数设置
     * @param paramVO
     */
    void saveParam(SysParamVO paramVO);

    /**
     * 修改参数设置
     * @param paramVO
     */
    void updateParam(SysParamVO paramVO);

    /**
     * 关闭或开放
     * @param param
     */
    void openOrClose(SysParamEntity param);
}

