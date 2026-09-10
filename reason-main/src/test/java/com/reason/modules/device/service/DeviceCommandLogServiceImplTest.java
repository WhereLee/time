package com.reason.modules.device.service;

import com.reason.modules.device.dao.DeviceCommandLogDao;
import com.reason.modules.device.service.impl.DeviceCommandLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指令流水 seq 启动对齐测试（批次4）：Redis 缺失/落后（旧 dump 回退）-> LUA 对齐取大 DB MAX；
 * 对齐失败不阻断启动（尽力恢复）——复现批次3 实测事故的预防侧
 */
@DisplayName("指令流水(批次4 seq 对齐)")
@ExtendWith(MockitoExtension.class)
class DeviceCommandLogServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private DeviceCommandLogDao deviceCommandLogDao;
    @InjectMocks
    private DeviceCommandLogServiceImpl commandLogService;

    @BeforeEach
    void injectBaseMapper() {
        //ServiceImpl 父类 baseMapper 字段（@InjectMocks 覆盖不到父类泛型字段）
        ReflectionTestUtils.setField(commandLogService, "baseMapper", deviceCommandLogDao);
    }

    @Test
    @DisplayName("启动对齐：DB MAX=11 / Redis 落后或缺失 -> LUA 校正（返回 1）")
    void 对齐_落后校正() {
        when(deviceCommandLogDao.selectMaps(any()))
                .thenReturn(List.of(Map.of("device_no", "BARRIER-B-01", "max_seq", 11L)));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        commandLogService.seedSeqFromDb();

        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), eq("11"));
    }

    @Test
    @DisplayName("启动对齐：Redis 现值领先（返回 0，不做覆盖）——判定已发生且不炸")
    void 对齐_领先不动() {
        when(deviceCommandLogDao.selectMaps(any()))
                .thenReturn(List.of(Map.of("device_no", "BARRIER-B-01", "max_seq", 11L)));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(0L);

        commandLogService.seedSeqFromDb();

        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), eq("11"));
    }

    @Test
    @DisplayName("对齐失败（Redis 抖动）：不抛异常不阻断启动（尽力恢复而非启动强依赖）")
    void 对齐_异常不阻断启动() {
        when(deviceCommandLogDao.selectMaps(any()))
                .thenReturn(List.of(Map.of("device_no", "BARRIER-B-01", "max_seq", 11L)));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        //不抛 = 通过（catch 全方法兜底）
        commandLogService.seedSeqFromDb();
    }
}
