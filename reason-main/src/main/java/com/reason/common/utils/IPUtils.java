/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * IP地址
 *
 * @author Mark sunlightcs@gmail.com
 */
@Slf4j
public class IPUtils {
	private static Logger logger = LoggerFactory.getLogger(IPUtils.class);

	/**
	 * （原 getIpAddr 已移除——T19：旧实现无条件信任 XFF/Proxy-Client-IP 等一切代理头且取整串，
	 * 任意客户端可伪造头冒充 IP。客户端 IP 解析统一走 com.reason.common.utils.ClientIpResolver：
	 * trusted-proxies 白名单 + XFF 只取可信代理末跳，非可信来源忽略一切代理头。）
	 */

    /**
     * 判断ip是否在线
     * @param ip
     * @param timeout 超时时间，单位：毫秒
     * @return
     */
    public static boolean isOnline(String ip,int timeout){
        try {
            int i=1;
            InetAddress address = null;
            try {
                if(ip==null || "".equals(ip)){
                    return false;
                }else{
                    address = InetAddress.getByName(ip);
                }
            } catch (Exception e) {
                return false;
            }
            while(address.isReachable(timeout)==false){
                if(i>3){
                    return false;
                }
                i++;
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            return false;
        }
    }
	
}
