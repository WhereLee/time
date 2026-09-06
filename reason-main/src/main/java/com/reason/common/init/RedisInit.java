package com.reason.common.init;

import com.reason.common.utils.Constant;
import com.reason.common.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Set;

@Slf4j
@Component("redisInit")
public class RedisInit {
    @Autowired
    private RedisUtils redisUtils;

    @PostConstruct
    public void init() {
        //清除系统参数和字典配置参数
        redisUtils.deleteAllKeys("redis_sys_*");
        log.info("=====清除REDIS_SYS_*====");
    }
}
