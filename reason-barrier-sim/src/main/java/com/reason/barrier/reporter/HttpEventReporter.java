package com.reason.barrier.reporter;

import com.reason.barrier.config.SimProperties;
import com.reason.barrier.model.BarrierState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP 状态上报（走平台设备事件通道，携 X-Device-Token）
 *
 * <p>上报失败只记日志不重试（样例边界：真实设备的上报重试/补偿是 monitor 块的活；
 * 但失败可见——日志是设备侧唯一的自省窗口）。</p>
 */
@Slf4j
@Component
public class HttpEventReporter implements EventReporter {

    private final SimProperties properties;
    private final RestTemplate restTemplate;

    public HttpEventReporter(SimProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public void report(String deviceNo, BarrierState state) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Token", properties.getToken());
        Map<String, Object> body = Map.of("deviceNo", deviceNo, "state", state.getCode());
        try {
            restTemplate.postForEntity(properties.getEventUrl(), new HttpEntity<>(body, headers), String.class);
            log.info("[上报成功] deviceNo={} state={}", deviceNo, state);
        } catch (RestClientException e) {
            //上报失败：平台可能离线。状态机继续走（杆是物理现实，不会因为平台没听见就不动）
            log.warn("[上报失败] deviceNo={} state={} cause={}", deviceNo, state, e.getMessage());
        }
    }
}
