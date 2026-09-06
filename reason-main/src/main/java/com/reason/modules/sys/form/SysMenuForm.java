package com.reason.modules.sys.form;

import lombok.Data;

@Data
public class SysMenuForm extends CommonForm{
    private Integer menuType;
    private String menuName;
    private Integer menuFid;

    @Override
    public String toString() {
        return "SysMenuForm{" +
                "menuType=" + menuType +
                ", menuName='" + menuName + '\'' +
                ", menuFid=" + menuFid +
                '}';
    }
}
