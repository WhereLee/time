package com.reason.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 设备模拟器启动类（扮演"十公里外的物理杆"）
 *
 * <p>无数据库、无 Redis：进程重启即回到初始态（真实设备上电无状态，靠上电自述对账）。
 * 契约：收平台指令 POST /cmd；动作完成后主动上报平台 POST /api/device/event。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BarrierSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(BarrierSimApplication.class, args);
    }
}
