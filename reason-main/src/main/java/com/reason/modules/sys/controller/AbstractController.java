/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.controller;

import com.reason.modules.sys.entity.SysUserEntity;
import com.reason.modules.sys.security.LoginUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller公共组件
 *
 * @author Mark sunlightcs@gmail.com
 */
public abstract class AbstractController {
	protected Logger logger = LoggerFactory.getLogger(getClass());
	
	protected SysUserEntity getUser() {
		return LoginUserHolder.getLoginUser();
	}

	protected Long getUserId() {
		return getUser().getUserId();
	}

	protected Long getRoleType() {
		return getUser().getRoleType();
	}
}
