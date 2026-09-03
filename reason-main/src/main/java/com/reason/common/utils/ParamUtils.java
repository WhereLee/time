package com.reason.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.reason.common.exception.RRException;
import com.reason.modules.sys.dao.SysParamDao;
import com.reason.modules.sys.entity.SysParamEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数工具类（口令尝试/锁定策略）
 */
@Component
public class ParamUtils {
    @Autowired
    private SysParamDao sysParamDao;
    @Autowired
    private RedisUtils redisUtils;

    /**
     * 口令最大尝试次数 0-不做限制
     * 账号限时锁定时间（单位：分钟）
     * @return
     */
    public Map<String, Integer> getAttemptLimtAndLockTime() {
        //attempt_limit 口令最大尝试次数，超过则限时锁定账号  0-不做限制  默认不做限制
        //lock_time 账号限时锁定时间（单位：分钟） 默认5分钟
        List<SysParamEntity> paramList = new ArrayList<>();
        //1.1 从Redis获取
        if (redisUtils.hasKey(Constant.REDIS_SYS_PARAM))
            paramList = (List<SysParamEntity>)redisUtils.getKeyValue(Constant.REDIS_SYS_PARAM);

        //1.2 从数据库查询 并存入Redis
        if (paramList == null || paramList.size() == 0)
            paramList = queryParam();

        //1.3 取值
        String attemptLimit = "";
        String lockTime = "";
        for (SysParamEntity param : paramList) {
            String paramKey = param.getParamKey();
            String paramValue = param.getParamValue();
            if (Constant.PARAM_ATTEMPT_LIMIT.equals(paramKey))
                attemptLimit = paramValue;
            if (Constant.PARAM_LOCK_TIME.equals(paramKey))
                lockTime = paramValue;
        }

        Map<String, Integer> map = new HashMap<>();

        try {
            map.put("attemptLimit", Integer.valueOf(StringUtils.replaceBlank(attemptLimit)));
        } catch (Exception e) {
            map.put("attemptLimit", 0);
        }

        try {
            map.put("lockTime", Integer.valueOf(StringUtils.replaceBlank(lockTime)));
        } catch (Exception e) {
            map.put("lockTime", 5);
        }

        return map;
    }

    /**
     * 口令定期变更  1-强制变更 2-提醒变更
     * 口令变更时限（单位：天）
     * @return
     */
    public Map<String, Integer> getChangeForceAndLimit() {
        //change_force 口令定期变更  1-强制变更 2-提醒变更 默认2
        //change_limit 口令变更时限（单位：天） 默认 30天
        List<SysParamEntity> paramList = new ArrayList<>();
        //1.1 从Redis获取
        if (redisUtils.hasKey(Constant.REDIS_SYS_PARAM))
            paramList = (List<SysParamEntity>)redisUtils.getKeyValue(Constant.REDIS_SYS_PARAM);

        //1.2 从数据库查询 并存入Redis
        if (paramList == null || paramList.size() == 0)
            paramList = queryParam();

        //1.3 取值
        String changeForce = "";
        String changeLimit = "";
        for (SysParamEntity param : paramList) {
            String paramKey = param.getParamKey();
            String paramValue = param.getParamValue();
            if (Constant.PARAM_CHANGE_FORCE.equals(paramKey))
                changeForce = paramValue;
            if (Constant.PARAM_CHANGE_LIMIT.equals(paramKey))
                changeLimit = paramValue;
        }

        Map<String, Integer> map = new HashMap<>();

        try {
            map.put("changeForce", Integer.valueOf(StringUtils.replaceBlank(changeForce)));
        } catch (Exception e) {
            map.put("changeForce", 2);
        }

        try {
            map.put("changeLimit", Integer.valueOf(StringUtils.replaceBlank(changeLimit)));
        } catch (Exception e) {
            map.put("changeLimit", 30);
        }

        return map;
    }

    /**
     * 查询 系统参数配置 并存入 Redis
     * @return
     */
    public List<SysParamEntity> queryParam() {
        //1.查询
        List<SysParamEntity> paramList = sysParamDao.selectList(
                new QueryWrapper<SysParamEntity>()
                        .eq("param_recycle", 0)
        );
        if (paramList == null || paramList.size() == 0)
            throw new RRException("参数未配置");

        //2.存入Redis
        if (redisUtils.hasKey(Constant.REDIS_SYS_PARAM))
            redisUtils.deleteKey(Constant.REDIS_SYS_PARAM);

        redisUtils.setKeyValue(Constant.REDIS_SYS_PARAM, paramList);

        return paramList;
    }
}
