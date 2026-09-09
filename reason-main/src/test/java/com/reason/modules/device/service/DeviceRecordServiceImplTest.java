package com.reason.modules.device.service;

import com.reason.common.exception.RRException;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.form.DeviceRecordForm;
import com.reason.modules.device.service.impl.DeviceRecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 设备台账服务测试（批次2，F2——B3 显式密钥登记）：
 * 传入合法 32hex → 采用入库；非法格式 → 拒绝（不落库）；空 → 平台随机生成（0.5 语义不变）；
 * 重复登记仍拒绝
 *
 * <p>ServiceImpl 父类 baseMapper 无法被 @InjectMocks 注入（纯单测限制，
 * 见 document/pitfalls/mybatis-plus-injectmocks-basemapper-unit-test.md——
 * 该文件为遗留 this/baseMapper 风格，按最小注入处理，不扩大改造面）。</p>
 */
@DisplayName("设备台账登记(批次2显式密钥)")
@ExtendWith(MockitoExtension.class)
class DeviceRecordServiceImplTest {

    private static final String DEVICE_NO = "BARRIER-B-01";
    private static final Pattern HEX32 = Pattern.compile("^[0-9a-f]{32}$");

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private DeviceRecordDao deviceRecordDao;
    @InjectMocks
    private DeviceRecordServiceImpl recordService;

    @BeforeEach
    void injectBaseMapper() {
        ReflectionTestUtils.setField(recordService, "baseMapper", deviceRecordDao);
    }

    private DeviceRecordForm form(String deviceNo, String secret) {
        DeviceRecordForm form = new DeviceRecordForm();
        form.setDeviceNo(deviceNo);
        form.setDeviceName("批量杆-01");
        form.setDeviceSecret(secret);
        return form;
    }

    private DeviceRecordEntity capturedInserted() {
        ArgumentCaptor<DeviceRecordEntity> captor = ArgumentCaptor.forClass(DeviceRecordEntity.class);
        verify(deviceRecordDao).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("显式合法 32hex 密钥：原样采用入库（登记脚本与 sim 密钥文件同源闭环）")
    void 显式密钥_合法hex采用() {
        String secret = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
        //ServiceImpl.getOne 底层调 BaseMapper.selectOne(wrapper, true)（default 方法）——
        //mock 未 stub 会执行真实现，须 doReturn 形式且参数为两参
        doReturn(null).when(deviceRecordDao).selectOne(any(), anyBoolean());

        recordService.saveRecord(form(DEVICE_NO, secret), 1L);

        assertThat(capturedInserted().getDeviceSecret()).isEqualTo(secret);
        assertThat(capturedInserted().getDeviceNo()).isEqualTo(DEVICE_NO);
        assertThat(capturedInserted().getDeviceState()).isEqualTo(0); //建档恒为未接入
    }

    @Test
    @DisplayName("显式密钥非法格式（口语化/长度不足）：拒绝且不落库（弱密钥被格式校验挡住）")
    void 显式密钥_非法格式拒绝() {
        doReturn(null).when(deviceRecordDao).selectOne(any(), anyBoolean());

        assertThatThrownBy(() -> recordService.saveRecord(form(DEVICE_NO, "weak-secret-123"), 1L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("密钥格式不合法");
        assertThatThrownBy(() -> recordService.saveRecord(form(DEVICE_NO, "ABCDEF0123456789ABCDEF0123456789"), 1L))
                .isInstanceOf(RRException.class); //大写 hex 也拒绝（格式锁定小写——登记脚本产出同源）
        verify(deviceRecordDao, never()).insert(any(DeviceRecordEntity.class));
    }

    @Test
    @DisplayName("密钥空（日常登记）：平台随机生成 32hex（0.5 语义不变）")
    void 密钥空_平台随机生成() {
        doReturn(null).when(deviceRecordDao).selectOne(any(), anyBoolean());

        recordService.saveRecord(form(DEVICE_NO, null), 1L);

        assertThat(capturedInserted().getDeviceSecret()).matches(HEX32);
    }

    @Test
    @DisplayName("重复登记：唯一性校验拒绝（DB 唯一索引兜底并发）")
    void 重复登记_拒绝() {
        DeviceRecordEntity exists = new DeviceRecordEntity();
        exists.setDeviceNo(DEVICE_NO);
        doReturn(exists).when(deviceRecordDao).selectOne(any(), anyBoolean());

        assertThatThrownBy(() -> recordService.saveRecord(form(DEVICE_NO, null), 1L))
                .isInstanceOf(RRException.class)
                .hasMessageContaining("已存在");
        verify(deviceRecordDao, never()).insert(any(DeviceRecordEntity.class));
    }
}
