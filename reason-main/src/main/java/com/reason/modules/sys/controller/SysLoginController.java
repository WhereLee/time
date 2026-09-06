/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.reason.common.annotation.SysLog;
import com.reason.common.utils.HttpContextUtils;
import com.reason.common.utils.IPUtils;
import com.reason.common.utils.Result;
import com.reason.modules.sys.entity.SysLoginEntity;
import com.reason.modules.sys.form.SysLoginForm;
import com.reason.modules.sys.service.SysCaptchaService;
import com.reason.modules.sys.service.SysUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

/**
 * 登录相关
 *
 * @author Mark sunlightcs@gmail.com
 */
@Tag(name = "登录登出")
@RestController
public class SysLoginController extends AbstractController {
	@Autowired
	private SysUserService sysUserService;
	@Autowired
	private SysCaptchaService sysCaptchaService;

	/**
	 * 验证码-没有调用
	 */
	@GetMapping("captcha.jpg")
	public void captcha(HttpServletResponse response, String uuid)throws IOException {
		response.setHeader("Cache-Control", "no-store, no-cache");
		response.setContentType("image/jpeg");

		//获取图片验证码
		BufferedImage image = sysCaptchaService.getCaptcha(uuid);

		ServletOutputStream out = response.getOutputStream();
		ImageIO.write(image, "jpg", out);
		IOUtils.closeQuietly(out);
	}

	/**
	 * 初始配置
	 */
	@Operation(summary = "初始配置", description = "查看初始配置；权限说明：不需要登录，不限制权限")
	@ApiOperationSupport(order = 1)
	@GetMapping("/sys/init")
	public Result<Map<String, Object>> init() {

		return Result.ok(sysUserService.getInitParam());
	}

	/**
	 * 登录  账号+验证码(企业微信|短信)
	 */
	@Operation(summary = "登录", description = "系统登录")
	@ApiOperationSupport(order = 21)
	@SysLog(module = "登录登出",func = "登录",value = "系统登录")
	@PostMapping("/sys/login")
	public Result<SysLoginEntity> login(@RequestBody SysLoginForm form) {
		/*boolean captcha = sysCaptchaService.validate(form.getUuid(), form.getCaptcha());
		if(!captcha){
			return R.error("验证码不正确");
		}*/

		//获取request
		HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
		String ip = IPUtils.getIpAddr(request);

		SysLoginEntity login = sysUserService.login(form, ip);
		return Result.ok(login, login.getMsg());
	}


	/**
	 * 退出
	 */
	//@SysLog(func = "退出",value = "退出")
	@Operation(summary = "退出", description = "退出登录")
	@ApiOperationSupport(order = 30)
	@SysLog(module = "登录登出",func = "退出",value = "退出登录")
	@PostMapping("/sys/logout")
	public Result logout() {
		sysUserService.logout(getUserId());

		return Result.ok();
	}
	
}
