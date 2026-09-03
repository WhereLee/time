
package com.reason;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;


@EnableAsync
@SpringBootApplication
@MapperScan({
		"com.reason.modules.*.dao",   // 系统模块的DAO路径（SysMenuDao所在包）
		"com.reason.modules.*.mapper" // 业务模块的Mapper路径（升降杆相关）
})
public class ReasonApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReasonApplication.class, args);
	}

}