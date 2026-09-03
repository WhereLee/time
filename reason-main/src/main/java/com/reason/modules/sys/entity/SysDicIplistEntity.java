package com.reason.modules.sys.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Schema(description = "IP黑白名单对象")
@Data
public class SysDicIplistEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "白名单列表 英文逗号分隔")
    private String whiteList;

    @Schema(description = "黑名单列表 英文逗号分隔")
    private String blackList;

    public SysDicIplistEntity() {};

    public SysDicIplistEntity(String whiteList, String blackList) {
        this.whiteList = whiteList;
        this.blackList = blackList;
    }
}
