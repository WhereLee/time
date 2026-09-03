package com.reason.modules.sys.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.reason.common.exception.RRException;
import com.reason.common.utils.*;
import com.reason.common.validator.Assert;
import com.reason.common.validator.ValidatorUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import com.reason.modules.sys.entity.*;
import com.reason.modules.sys.form.SysDictionaryForm;
import com.reason.modules.sys.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.reason.modules.sys.dao.SysDictionaryDao;
import com.reason.modules.sys.service.SysDictionaryService;

import java.util.List;

@Slf4j
@Service("sysDictionaryService")
public class SysDictionaryServiceImpl extends ServiceImpl<SysDictionaryDao, SysDictionaryEntity> implements SysDictionaryService {
    @Autowired
    private SysDictionaryDao sysDictionaryDao;
    @Autowired
    private RedisUtils redisUtils;


    /**
     * 分页查询字典
     * @param form
     * @return
     */
    @Override
    public PageUtils queryPage(SysDictionaryForm form) {
        //查询条件
        String dicSort = form.getDicSort();
        String dicValue = form.getDicValue();

        //查询
        IPage<SysDictionaryEntity> page = this.page(
                new Query<SysDictionaryEntity>().getPage(new MapUtils()
                        .put(Constant.PAGE,form.getPage()).put(Constant.LIMIT,form.getLimit())),
                new QueryWrapper<SysDictionaryEntity>()
                        .eq("dic_status", 0)
                        .eq(StringUtils.isNotBlank(dicSort), "dic_sort", dicSort)
                        .like(StringUtils.isNotBlank(dicValue), "dic_value", dicValue)
        );

        return new PageUtils(page);
    }



    /**
     * 下拉查询字典
     * @param form
     * @return
     */
    @Override
    public List<SysDictionaryEntity> queryList(SysDictionaryForm form) {
        //查询条件
        String dicSort = form.getDicSort();
        String dicValue = form.getDicValue();

        //查询
        List<SysDictionaryEntity> list = this.list(
                new QueryWrapper<SysDictionaryEntity>()
                        .eq("dic_status", 0)
                        .eq(StringUtils.isNotBlank(dicSort), "dic_sort", dicSort)
                        .like(StringUtils.isNotBlank(dicValue), "dic_value", dicValue)
        );

        return list;
    }

    /**
     * 根据ID查询字典
     * @param dicId
     * @return
     */
    @Override
    public SysDictionaryEntity getInfo(Long dicId) {
        //1.查询字典
        SysDictionaryEntity dic = this.getById(dicId);

        if (dic == null || !dic.valid())
            throw new RRException("该字典不存在或已删除");

        //2.查询其他信息 TODO

        return dic;
    }

    /**
     * 新增字典
     * @param vo
     * @param creator
     */
    @Override
    public void save(SysDictionaryVO vo, Long creator) {
        //1.数据校验
        ValidatorUtils.validateEntity(vo,AddGroup.class);

        String dicSort = vo.getDicSort();
        String dicValue = vo.getDicValue();

        //2.校验其他 TODO
        //校验值是否已存在
        long count = this.count(
                new QueryWrapper<SysDictionaryEntity>()
                        .eq("dic_status", 0)
                        .eq("dic_sort", dicSort)
                        .eq("dic_value", dicValue)
        );
        if (count > 0)
            throw new RRException("该值已经存在");

        //3.生成dicKey  当前最大Key+1
        Integer dicKey = sysDictionaryDao.getLatestKey(dicSort);

        //3.创建实体类
        SysDictionaryEntity dic = new SysDictionaryEntity(vo, 1, dicKey+1, creator);

        //4.新增字典
        this.save(dic);
    }

    /**
     * 修改字典
     * 分类 dicSort不能修改
     * @param vo
     */
    @Override
    public void update(SysDictionaryVO vo) {
        //1.数据校验
        ValidatorUtils.validateEntity(vo,UpdateGroup.class);

        Long dicId = vo.getDicId();

        //2.校验 id 是否存在
        SysDictionaryEntity old = this.getById(dicId);
        if (old == null || !old.valid())
            throw new RRException("该字典信息不存在或已删除");


        String dicSort = old.getDicSort();
        String dicValue = vo.getDicValue();

        //3.校验其他 TODO
        //校验值是否已存在
        long count = this.count(
                new QueryWrapper<SysDictionaryEntity>()
                        .eq("dic_status", 0)
                        .eq("dic_sort", dicSort)
                        .eq("dic_value", dicValue)
                        .ne("dic_id", dicId)
        );
        if (count > 0)
            throw new RRException("该值已经存在");

        //4.创建实体类
        SysDictionaryEntity dic = new SysDictionaryEntity(vo, 2, null, null);

        //5.修改字典
        this.updateById(dic);

    }

    /**
     * 逻辑删除字典
     * @param dicId
     */
    @Override
    public void delete(Long dicId) {
        //1.校验参数
        Assert.isNull(dicId,"字典ID不能为空");

        //2.校验 id 是否存在
        SysDictionaryEntity old = this.getById(dicId);
        if (old == null || !old.valid())
            throw new RRException("该字典信息不存在或已删除");

        //3.校验其他 TODO

        //4.逻辑删除  status = id
        this.updateById(new SysDictionaryEntity(dicId));
    }


    /**
     * 查询IP黑白名单
     * @return
     */
    @Override
    public SysDicIplistEntity getIpList() {
        //1.从Redis获取
        if (redisUtils.hasKey(Constant.REDIS_SYS_DIC_IPLIST))
            return (SysDicIplistEntity) redisUtils.getKeyValue(Constant.REDIS_SYS_DIC_IPLIST);

        //Redis中没有则查询数据库
        //2.查询字典-黑白名单
        List<SysDictionaryEntity> disList = this.list(
                new QueryWrapper<SysDictionaryEntity>()
                        .eq("dic_sort", Constant.DIC_SORT_IPLIST)
        );

        if (disList == null || disList.size() == 0)
            return new SysDicIplistEntity();

        //3.数据处理
        String whiteList = "";
        String blackList = "";
        for (SysDictionaryEntity dic : disList) {
            String dicKey = dic.getDicKey();
            String dicValue = dic.getDicValue();
            if (Constant.DIC_KEY_WHITE_LIST.equals(dicKey))
                whiteList = dicValue;
            if (Constant.DIC_KEY_BLACK_LIST.equals(dicKey))
                blackList = dicValue;
        }

        SysDicIplistEntity iplist = new SysDicIplistEntity(whiteList, blackList);

        //4.存入Redis
        redisUtils.setKeyValue(Constant.REDIS_SYS_DIC_IPLIST, iplist);

        return iplist;
    }

    /**
     * 设置IP黑白名单
     * @param iplistVO
     */
    @Override
    public void setIpList(SysDicIplistVO iplistVO) {
        //0.校验
        ValidatorUtils.validateEntity(iplistVO);

        //1.设置
        sysDictionaryDao.setIpList(iplistVO);

        //2.清除redis
        redisUtils.delete(Constant.REDIS_SYS_DIC_IPLIST);
    }
}
