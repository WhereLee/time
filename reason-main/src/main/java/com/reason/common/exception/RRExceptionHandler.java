/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.common.exception;

import com.reason.common.utils.LogThrottle;
import com.reason.common.utils.Result;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 异常处理器
 *
 * @author Mark sunlightcs@gmail.com
 */
@Slf4j
@RestControllerAdvice
@Hidden // Boot 3.5 兼容：knife4j 4.5.0 内置 springdoc 2.3.0 扫描 @RestControllerAdvice 时调用已被 Spring 6.2 移除的 ControllerAdviceBean(Object) 构造器，导致 /v3/api-docs 500；@Hidden 让 springdoc 跳过本类（异常处理器无需进文档）。详见 document/pitfalls/springdoc-controlleradvice-boot34-incompat.md
public class RRExceptionHandler {

	private final LogThrottle logThrottle;

	public RRExceptionHandler(LogThrottle logThrottle) {
		this.logThrottle = logThrottle;
	}

	/**
	 * 处理自定义异常
	 */
	@ExceptionHandler(RRException.class)
	public Result handleRRException(RRException e){
		Result result = new Result();
		result.setCode(e.getCode());
		result.setMsg(e.getMessage());

		log.error(e.getMessage(), e);
		return result;
	}

	/**
	 * ResponseStatusException 透传（0.7：设备通道 401/400 等语义化状态不被全局 Exception 兜底吞成 200）
	 * ——全局 handleException 只应处理真正未预期的异常；显式状态异常必须保持 HTTP 语义
	 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Result> handleResponseStatus(ResponseStatusException e) {
		Result result = Result.error(e.getStatusCode().value(), e.getReason());
		//批次8 P3：拒绝路径日志节流——B4 实测假签名洪峰下本行为每条 401 各打一条 warn
		//（≈100 行/秒、1.3MB/分钟）；现按状态码分键每 5s 首条+抑制计数（可见性不丢，输出量有界）
		int status = e.getStatusCode().value();
		logThrottle.warn(log, "http-status:" + status,
				() -> "HTTP 状态异常透传 status=" + status + " reason=" + e.getReason());
		return ResponseEntity.status(e.getStatusCode()).body(result);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public Result handlerNoFoundException(Exception e) {
		log.error(e.getMessage(), e);
		return Result.error(404, "路径不存在，请检查路径是否正确");
	}

	@ExceptionHandler(DuplicateKeyException.class)
	public Result handleDuplicateKeyException(DuplicateKeyException e){
		log.error(e.getMessage(), e);
		return Result.error("数据库中已存在该记录");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public Result handleAuthorizationException(AccessDeniedException e){
		log.error(e.getMessage(), e);
		return Result.error("没有权限，请联系管理员授权");
	}

	@ExceptionHandler(Exception.class)
	public Result handleException(Exception e){
		log.error(e.getMessage(), e);
		return Result.error();
	}
}
