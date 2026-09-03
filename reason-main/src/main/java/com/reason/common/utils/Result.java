package com.reason.common.utils;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.http.HttpStatus;

import java.io.Serializable;

/**
 * 返回结果-原先的Map改为对象(Map Knife4j不能显示Map响应参数)
 */
@Schema(description = "返回结果")
@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态码 0-成功 其他-失败")
    private Integer code;
    @Schema(description = "返回信息")
    private String msg;
    @Schema(description = "返回对象")
    private T data;

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Result() {
        this.code = 0;
        this.msg = "操作成功";
    }

    public static Result ok() {
        return new Result();
    }

    public static Result ok(Object data) {
        Result result = new Result();
        result.setData(data);
        return result;
    }

    public static Result ok(Object data,String msg) {
        Result result = new Result();
        result.setData(data);
        result.setMsg(msg);
        return result;
    }

    public static Result error(Integer code,String msg) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public static Result error(Integer code,String msg,Object data) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    public static Result error(String msg) {
        return error(HttpStatus.SC_INTERNAL_SERVER_ERROR, msg);
    }

    public static Result error() {
        return error(HttpStatus.SC_INTERNAL_SERVER_ERROR, "未知异常，请联系管理员");
    }
}
