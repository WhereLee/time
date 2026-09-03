package com.reason.modules.sys.entity;


import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;

/**
 * 
 * 
 * @date 2020-04-22 15:02:49
 */
@TableName("sys_log")
@Schema(description = "系统日志对象")
@Data
public class SysLogEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 主键ID
	 */
	@TableId
	@Schema(description = "日志ID")
	private Long logId;

	@Schema(description = "日志类型 1-WEB端 2-APP端")
	private Integer logType;
	/**
	 * 访问的URL 如：/api/user/login
	 */
	@Schema(description = "访问URL")
	private String logUrl;
	/**
	 * 参数
	 */
	@Schema(description = "参数")
	private String logParams;
	/**
	 * 执行结果 0-成功 1-失败
	 */
	@Schema(description = "执行结果 0-成功 1-失败")
	private Integer logState;
	/**
	 * 执行信息 成功；失败（异常信息）
	 */
	@Schema(description = "执行信息")
	private String logMessage;

	@Schema(description = "返回信息")
	private String logReturn;
	/**
	 * 异常信息
	 */
	@Schema(description = "异常信息")
	private String logError;
	/**
	 * 操作所属功能模块 菜单管理、OLT管理 等
	 */
	@Schema(description = "模块")
	private String logModule;
	/**
	 * 操作 查询、保存等
	 */
	@Schema(description = "操作")
	private String logFunc;
	/**
	 * 操作说明 查询用户等
	 */
	@Schema(description = "操作说明")
	private String logOperation;
	/**
	 * 调用的方法
	 */
	@Schema(description = "调用方法")
	private String logMethod;
	/**
	 * 请求的IP
	 */
	@Schema(description = "请求IP")
	private String logIp;
	/**
	 * 请求的浏览器
	 */
	@Schema(description = "请求浏览器")
	private String logBrowser;
	/**
	 * 执行时长 单位 毫秒
	 */
	@Schema(description = "执行时长（毫秒）")
	private Long logDuration;
	/**
	 * 操作人 null是表示没有获取到 -1是系统 0是websocket的虚拟用
	 */
	@Schema(description = "操作人")
	private Long logCreator;
	/**
	 * 操作人用户名
	 */
	@Schema(description = "操作员姓名")
	private String logCreatorName;

	@Schema(description = "OPENID")
	private String logOpenid;

	@Schema(description = "昵称")
	private String logNickname;

	@Schema(description = "头像URL")
	private String logProfile;

	@Schema(description = "手机")
	private String logMobile;
	/**
	 * 创建时的时间戳，单位为秒
	 */
	@Schema(description = "操作时间戳（秒）")
	private Long logCreatetime;
}
