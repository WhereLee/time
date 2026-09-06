package com.reason.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reason.common.utils.PageUtils;
import com.reason.modules.sys.entity.*;
import com.reason.modules.sys.form.SysDictionaryForm;
import com.reason.modules.sys.vo.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * 
 *
 * @author author
 * @date 2023-03-09 15:18:02
 */
public interface SysDictionaryService extends IService<SysDictionaryEntity> {
    /**
     * 分页查询字典
     * @param form
     * @return
     */
    PageUtils queryPage(SysDictionaryForm form);

    /**
     * 下拉查询字典
     * @param form
     * @return
     */
    List<SysDictionaryEntity> queryList(SysDictionaryForm form);

    /**
     * 根据ID查询字典
     * @param dicId
     * @return
     */
    SysDictionaryEntity getInfo(Long dicId);

    /**
     * 新增字典
     * @param vo
     * @param creator
     */
    void save(SysDictionaryVO vo, Long creator);

    /**
     * 修改字典
     * @param vo
     */
    void update(SysDictionaryVO vo);

    /**
     * 逻辑删除字典
     * @param dicId
     */
    void delete(Long dicId);

    /**
     * 查询IP黑白名单
     * @return
     */
    SysDicIplistEntity getIpList();

    /**
     * 设置IP黑白名单
     * @param iplistVO
     */
    void setIpList(SysDicIplistVO iplistVO);

}
