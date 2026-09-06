package com.reason.modules.sys.dao;

import com.reason.modules.sys.entity.SysDictionaryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reason.modules.sys.vo.*;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 
 * 
 * @author author
 * @date 2023-03-09 15:18:02
 */
@Repository
public interface SysDictionaryDao extends BaseMapper<SysDictionaryEntity> {
    /**
     * 取最大的Key
     * @param dicSort
     * @return
     */
    Integer getLatestKey(String dicSort);

    /**
     * 设置IP黑白名单
     * @param iplistVO
     */
    void setIpList(@Param("iplistVO") SysDicIplistVO iplistVO);
}
