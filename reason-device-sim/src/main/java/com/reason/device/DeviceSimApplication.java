package com.reason.device;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 设备模拟服务启动类（独立小进程，端口 8300）
 *
 * <p>职责边界：扮演真实停车设备——事件上报（入场/出场/取消）调用 reason-main
 * 设备接入接口；接收 reason-main 下发的控制指令（开关闸/落锁）打日志模拟执行。
 * 无 DB/Redis，纯内存 + HTTP，进程崩溃即恢复初始态（真实设备上电同样无状态）。</p>
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class DeviceSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceSimApplication.class, args);
    }
}
