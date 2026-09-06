package com.reason.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reason.common.exception.RRException;
import com.reason.modules.device.config.DeviceChannelProperties;
import com.reason.modules.device.dao.DeviceRecordDao;
import com.reason.modules.device.entity.DeviceRecordEntity;
import com.reason.modules.device.service.DeviceCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 设备指令服务实现
 *
 * <p>指令下发 = 平台唯一的"指挥"动作：查档案 → HTTP 发给模拟器（十公里外的设备）→
 * 设备立刻回执 ACCEPTED（动作异步执行，事件随后主动上报）。
 * 下发失败（档案不存在/设备无响应/设备拒绝动作）显式抛错，由管理端感知；
 * 但无论成败，此处都不写台账状态——状态等事件。</p>
 */
@Slf4j
@Service("deviceCommandService")
public class DeviceCommandServiceImpl implements DeviceCommandService {

    /** 指令端点相对路径（模拟器侧 /cmd，与 reason-barrier-sim 契约一致） */
    private static final String CMD_PATH = "/cmd";

    private final DeviceRecordDao recordDao;
    private final DeviceChannelProperties channelProperties;
    private final RestTemplate restTemplate;

    public DeviceCommandServiceImpl(DeviceRecordDao recordDao,
                                    DeviceChannelProperties channelProperties) {
        this.recordDao = recordDao;
        this.channelProperties = channelProperties;
        //通道级超时：设备在"十公里外"，网络不可靠——发指令不能无限等（指令超时是反馈闭环的前置）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public void open(String deviceNo) {
        send(deviceNo, "OPEN");
    }

    @Override
    public void close(String deviceNo) {
        send(deviceNo, "CLOSE");
    }

    private void send(String deviceNo, String action) {
        //1. 档案必须存在：对未登记的设备发指令 = 对着空气说话
        DeviceRecordEntity record = recordDao.selectOne(
                new LambdaQueryWrapper<DeviceRecordEntity>()
                        .eq(DeviceRecordEntity::getDeviceNo, deviceNo));
        if (record == null) {
            throw new RRException("设备未登记: " + deviceNo);
        }

        //2. 下发指令到模拟器；动作合法性由设备侧裁决（平台不猜设备真实状态）
        String url = channelProperties.getSimBaseUrl() + CMD_PATH;
        Map<String, String> payload = Map.of("deviceNo", deviceNo, "action", action);
        try {
            Map<?, ?> resp = restTemplate.postForObject(url, payload, Map.class);
            Object code = resp == null ? null : resp.get("code");
            if (resp == null || !Integer.valueOf(0).equals(code)) {
                Object msg = resp == null ? "空响应" : resp.get("msg");
                throw new RRException("设备拒绝指令: " + msg);
            }
            //3. 指令已受理：不改台账状态（等设备事件回来再更新——反馈闭环铁律）
            log.info("指令已下发 deviceNo={} action={} -> 等待设备事件回报", deviceNo, action);
        } catch (RestClientException e) {
            //网络层失败：设备离线/不可达，显式告警，不留假状态
            log.warn("指令下发失败 deviceNo={} action={} cause={}", deviceNo, action, e.getMessage());
            throw new RRException("设备无响应(连接失败): " + deviceNo);
        }
    }
}
