package com.reason.modules.sys.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Schema(description = "登录返回实体")
@Data
public class SysLoginEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "token")
    private String token;

    @Schema(description = "token有效期")
    private Integer expire;

    @Schema(description = "登录用户的userId")
    private Long userId;

    @Schema(description = "登录用户所拥有的角色Id列表")
    private List<Long> roleIdList;

    private String msg;

    public SysLoginEntity() {}

    public SysLoginEntity(String token, Integer expire) {
        this.token = token;
        this.expire = expire;
        this.msg = "登录成功";
    }
}
