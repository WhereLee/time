/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.modules.sys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reason.modules.sys.dao.SysUserTokenDao;
import com.reason.modules.sys.entity.SysLoginEntity;
import com.reason.modules.sys.entity.SysUserTokenEntity;
import com.reason.modules.sys.security.TokenGenerator;
import com.reason.modules.sys.service.SysUserTokenService;
import org.springframework.stereotype.Service;


@Service("sysUserTokenService")
public class SysUserTokenServiceImpl extends ServiceImpl<SysUserTokenDao, SysUserTokenEntity> implements SysUserTokenService {
	//6小时后过期 单位 秒
	private final static int EXPIRE = 60 * 60 * 6;

	/**
	 * 生成token
	 * @param userId  用户ID
	 */
	@Override
	public SysLoginEntity createToken(Long userId) {
		//生成一个token
		String token = TokenGenerator.generateValue();

		//当前时间
		Long timestamp = System.currentTimeMillis()/1000;
		//过期时间
		Long expiretime = timestamp + EXPIRE;

		//判断是否生成过token
		SysUserTokenEntity tokenEntity = this.getById(userId);
		if(tokenEntity == null){
			tokenEntity = new SysUserTokenEntity();
			tokenEntity.setUserId(userId);
			tokenEntity.setToken(token);
			tokenEntity.setUpdatetime(timestamp);
			tokenEntity.setExpiretime(expiretime);

			//保存token
			this.save(tokenEntity);
		}else{
			tokenEntity.setToken(token);
			tokenEntity.setUpdatetime(timestamp);
			tokenEntity.setExpiretime(expiretime);

			//更新token
			this.updateById(tokenEntity);
		}

		SysLoginEntity login = new SysLoginEntity(token,EXPIRE);

		return login;
	}

	/**
	 * 退出，修改token值
	 * @param userId  用户ID
	 */
	@Override
	public void updateToken(Long userId) {
		//生成一个token
		String token = TokenGenerator.generateValue();

		//修改token
		SysUserTokenEntity tokenEntity = new SysUserTokenEntity();
		tokenEntity.setUserId(userId);
		tokenEntity.setToken(token);
		this.updateById(tokenEntity);
	}
}
