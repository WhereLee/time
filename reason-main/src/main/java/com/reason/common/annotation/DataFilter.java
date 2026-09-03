package com.reason.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataFilter {
    /**  表的别名 */
    String tableAlias() default "";

    /**  true：限制角色数据权限 */
    boolean roleFilter() default false;

    /**  true：限制菜单数据权限 */
    boolean menuFilter() default false;

    /** true：现在用户数据权限 */
    boolean userFilter() default false;

    /**  角色ID */
    String roleId() default "role_id";

    /**  菜单ID */
    String menuId() default "menu_id";

    /** 用户ID */
    String userId() default "user_id";
}
