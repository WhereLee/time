package com.reason.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 接口文档配置（SpringDoc + Knife4j）
 * 文档地址：/doc.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("reason-faster 开发框架")
                        .description("个人开发框架（Spring Boot 3）接口文档")
                        .version("1.0.0")
                        .contact(new Contact().name("reason")))
                .addSecurityItem(new SecurityRequirement().addList("token"))
                .components(new Components().addSecuritySchemes("token",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("token")));
    }
}