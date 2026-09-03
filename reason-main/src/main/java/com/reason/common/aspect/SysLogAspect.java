/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.common.aspect;

import com.alibaba.fastjson2.JSONObject;
import com.reason.common.utils.JsonUtil;
import com.reason.common.annotation.SysLog;
import com.reason.common.exception.RRException;
import com.reason.common.utils.StringUtils;
import com.reason.modules.sys.entity.SysLogEntity;
import com.reason.common.utils.HttpContextUtils;
import com.reason.common.utils.IPUtils;
import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.service.SysLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;


/**
 * 系统日志，切面处理类
 *
 * @author Mark sunlightcs@gmail.com
 */
@Slf4j
@Aspect
@Component
public class SysLogAspect {
	@Autowired
	private SysLogService sysLogService;
	
	@Pointcut("@annotation(com.reason.common.annotation.SysLog)")
	public void logPointCut() {}

	@Around("logPointCut()")
	public Object around(ProceedingJoinPoint point) throws Throwable {
		Long beginTime = System.currentTimeMillis();//开始时间(毫秒)
		Integer logState = 0;//执行结果 0-成功 1-失败
		String logMessage = "成功";//执行信息 成功；失败（异常信息）
		String logError = null;//异常信息
		Object logReturn = null;//返回结果
		try {
			//执行方法
			logReturn = point.proceed();
			return logReturn;

		} catch (Exception e) {
			if (e instanceof RRException) {
				logMessage = "失败："+e.getMessage();
			} else if (e instanceof NoHandlerFoundException) {
				logMessage = "失败：路径不存在";
			} else if (e instanceof DuplicateKeyException) {
				logMessage = "失败：数据库中已存在该记录";
			} else if (e instanceof AuthorizationException) {
				logMessage = "失败：没有权限，请联系管理员授权";
			} else {
				logMessage = "失败：异常";
			}

			logState = 1;
			logError = StringUtils.substring(e.toString(),0,2000);

			throw e;
		} finally {
			//执行时长(毫秒)
			Long duration = System.currentTimeMillis() - beginTime;

			//日志对象
			SysLogEntity sysLog = new SysLogEntity();
			sysLog.setLogType(1);//日志类型 1-WEB端 2-APP端
			sysLog.setLogState(logState);
			sysLog.setLogMessage(logMessage);
			sysLog.setLogReturn(JSONObject.toJSONString(logReturn));
			sysLog.setLogError(logError);
			sysLog.setLogDuration(duration);
			sysLog.setLogCreatetime(beginTime/1000);

			//保存日志
			saveSysLog(point, sysLog);

		}

	}


	/**
	 * 成功日志
	 * @param joinPoint
	 * @param sysLog
	 */
	private void saveSysLog(ProceedingJoinPoint joinPoint, SysLogEntity sysLog) {
		try {
			MethodSignature signature = (MethodSignature) joinPoint.getSignature();
			Method method = signature.getMethod();

			SysLog syslog = method.getAnnotation(SysLog.class);
			if (syslog != null) {
				//注解上的描述
				sysLog.setLogModule(syslog.module());
				sysLog.setLogFunc(syslog.func());
				sysLog.setLogOperation(syslog.value());
			}

			//请求的方法名
			String className = joinPoint.getTarget().getClass().getName();
			String methodName = signature.getName();
			sysLog.setLogMethod(className + "." + methodName + "()");

			//请求的参数
			Object[] args = joinPoint.getArgs();
			try {
				String params = JsonUtil.toJsonString(args);
				sysLog.setLogParams(params);
			} catch (Exception e) {}

			//获取request
			HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
			//设置IP地址 TODO
			sysLog.setLogIp(IPUtils.getIpAddr(request));
			sysLog.setLogUrl(request.getRequestURI());
			sysLog.setLogBrowser(request.getHeader("User-Agent"));

			//用户名
			//String username = ((SysUserEntity) SecurityUtils.getSubject().getPrincipal()).getUsername();
			SysUserEntity creator = (SysUserEntity) SecurityUtils.getSubject().getPrincipal();
			if (creator != null) {
				String userName = creator.getUserName();
				String userRealname = StringUtils.isBlank(creator.getUserRealname()) ? "" : creator.getUserRealname();

				sysLog.setLogCreator(creator.getUserId());
				sysLog.setLogCreatorName(userName + "(" + userRealname + ")");
			}

			/*开发员也记日志 2020年10月24日
			if (creator.isDeveloper()) {//开发员不记日志
				return;
			}*/

			//保存系统日志
			sysLogService.save(sysLog);

		} catch (Exception e) {
			log.info("保存日志失败");
			log.error(e.getMessage(),e);
		}
	}
}
