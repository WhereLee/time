package com.reason.config;

import com.reason.common.utils.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.MultipartConfigElement;
import java.io.File;

/**
 *配置文件上传路径
 *centOS 会删除默认文件上传路径
 */
@Slf4j
@Configuration
public class HttpMultipartConfig {
    /**
     * 文件上传路径
     * @return
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        //临时上传路径
        String tempPath = Constant.MULTIPART_TEMP_PATH;
        File tmpFile =new File (tempPath);
        if(!tmpFile.exists()){
            tmpFile.mkdirs();
        }
        String location = tmpFile.getAbsolutePath();
        log.info("temp location:{}", location);
        factory.setLocation(location);

        return factory.createMultipartConfig();
    }
}
