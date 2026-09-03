package com.reason.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExportLog {

    String module() default "";//功能模块
    String func() default "";//操作：增删改查
    String value() default "";//说明
}
