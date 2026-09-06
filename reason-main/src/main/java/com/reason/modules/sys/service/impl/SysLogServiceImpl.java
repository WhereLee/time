package com.reason.modules.sys.service.impl;

import com.reason.common.exception.RRException;
import com.reason.common.utils.*;
import com.reason.modules.sys.form.SysLogForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.reason.modules.sys.dao.SysLogDao;
import com.reason.modules.sys.entity.SysLogEntity;
import com.reason.modules.sys.service.SysLogService;

import java.util.List;


@Service("sysLogService")
public class SysLogServiceImpl extends ServiceImpl<SysLogDao, SysLogEntity> implements SysLogService {

    /**
     * 查询日志信息-分页
     * @param form
     * @return
     */
    @Override
    public PageUtils queryPage(SysLogForm form) {
        IPage<SysLogEntity> page = this.page(
                new Query<SysLogEntity>().getPage(new MapUtils()
                        .put(Constant.PAGE,form.getPage()).put(Constant.LIMIT,form.getLimit())
                        .put(Constant.ORDER_FIELD,"log_id").put(Constant.ORDER,"desc")),
                new QueryWrapper<SysLogEntity>()
                        .eq(form.getLogState()!= null,"log_state", form.getLogState())
                        .like(StringUtils.isNotBlank(form.getLogModule()),"log_module", form.getLogModule())
                        .like(StringUtils.isNotBlank(form.getLogFunc()),"log_func", form.getLogFunc())
                        .like(StringUtils.isNotBlank(form.getLogCreatorName()),"log_creator_name", form.getLogCreatorName())
                        .like(StringUtils.isNotBlank(form.getLogParams()),"log_params",form.getLogParams())
                        .ge(form.getStarttime()!= null,"log_createtime",form.getStarttime())
                        .le(form.getEndtime()!= null,"log_createtime",form.getEndtime())
        );

        return new PageUtils(page);
    }

    /**
     * 根据ID 查询日志
     * @param logId
     * @return
     */
    @Override
    public SysLogEntity getInfo(Long logId) {
        //1.查询日志信息
        SysLogEntity log = this.getById(logId);

        if (log == null || log.getLogId() == null) {
            throw new RRException("该日志信息不存在或已删除");
        }

        return log;
    }

}
