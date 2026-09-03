package com.reason.common.utils;

import lombok.Data;

/**
 * Excel 标题栏
 */
@Data
public class XlsHead {
    private String title;       //标题栏
    private Integer width;      //列宽

    public XlsHead(){}

    public XlsHead(String title,Integer width) {
        this.title = title;
        this.width = width;
    }
}
