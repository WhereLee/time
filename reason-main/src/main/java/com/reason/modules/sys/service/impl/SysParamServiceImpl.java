package com.reason.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.common.exception.RRException;
import com.reason.common.utils.*;
import com.reason.common.validator.Assert;
import com.reason.common.validator.ValidatorUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import com.reason.modules.sys.dao.SysParamDao;
import com.reason.modules.sys.entity.SysParamEntity;
import com.reason.modules.sys.form.SysParamForm;
import com.reason.modules.sys.service.SysParamService;
import com.reason.modules.sys.vo.SysParamVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service("sysParamService")
public class SysParamServiceImpl extends ServiceImpl<SysParamDao, SysParamEntity> implements SysParamService {
    @Autowired
    private RedisUtils redisUtils;

    /**
     * 查询参数列表-分页
     * @param form
     * @return
     */
    @Override
    public PageUtils queryPage(SysParamForm form) {
        IPage<SysParamEntity> page = this.page(
                new Query<SysParamEntity>().getPage(new MapUtils()
                        .put(Constant.PAGE,form.getPage()).put(Constant.LIMIT,form.getLimit())),
                new QueryWrapper<SysParamEntity>()
                        .like(StringUtils.isNotBlank(form.getParamName()),"param_name", form.getParamName())
        );

        return new PageUtils(page);
    }

    /**
     * 新增参数设置
     * @param paramVO
     */
    @Override
    public void saveParam(SysParamVO paramVO) {
        //1.校验参数
        ValidatorUtils.validateEntity(paramVO,AddGroup.class);

        //2.创建参数实体类
        SysParamEntity param = new SysParamEntity(paramVO,1);

        //2.新增
        this.save(param);

        //4.删除Redis 系统参数缓存
        if (redisUtils.hasKey(Constant.REDIS_SYS_PARAM))
            redisUtils.deleteKey(Constant.REDIS_SYS_PARAM);

    }

    /**
     * 修改参数设置
     * @param paramVO
     */
    @Override
    public void updateParam(SysParamVO paramVO) {
        //1.校验参数
        ValidatorUtils.validateEntity(paramVO,UpdateGroup.class);

        //2.校验 id 是否存在
        SysParamEntity old = this.getById(paramVO.getParamId());
        if (old == null || old.getParamId()== null) {
            throw new RRException("该参数信息不存在或已删除");
        }

        //2.创建参数实体类
        SysParamEntity param = new SysParamEntity(paramVO,2);

        //3.参数名称、参数Key、参数说明不能修改
        param.setParamName(null);
        param.setParamKey(null);
        param.setParamComment(null);

        //4.修改
        this.updateById(param);

        //5.删除Redis 系统参数缓存
        if (redisUtils.hasKey(Constant.REDIS_SYS_PARAM))
            redisUtils.deleteKey(Constant.REDIS_SYS_PARAM);

    }

    /**
     * 关闭或开放
     * @param param
     */
    @Override
    public void openOrClose(SysParamEntity param) {
        //1.校验参数
        Assert.isNull(param.getParamId(),"参数ID不能为空");

        //2.校验 id 是否存在
        SysParamEntity old = this.getById(param.getParamId());
        if (old == null || old.getParamId()== null) {
            throw new RRException("该参数信息不存在或已删除");
        }

        //3.开放或关闭
        this.updateById(param);

        //4.删除Redis 系统参数缓存
        if (redisUtils.hasKey(Constant.REDIS_SYS_PARAM))
            redisUtils.deleteKey(Constant.REDIS_SYS_PARAM);

    }
}