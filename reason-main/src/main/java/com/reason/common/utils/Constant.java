/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 *
 * https://www.renren.io
 *
 * 版权所有，侵权必究！
 */

package com.reason.common.utils;

/**
 * 常量
 *
 * @author Mark sunlightcs@gmail.com
 */
public class Constant {
    /**
     * 开发员角色ID
     */
	public static final Long DEVELOPER_ROLEID = 1L;
    /**
     * 系统管理员角色ID
     */
    public static final Long SYSADMIN_ROLEID = 2L;
    /**
     * 开发员用户ID
     */
    public static final Long DEVELOPER_USERID = 1L;
    /**
     * 开发员角色类型
     */
    public static final Long DEVELOPER_ROLETYPE = 1L;
    /**
     * 系统管理员角色类型
     */
    public static final Long SYSADMIN_ROLETYPE = 2L;

    /**
     * 当前页码
     */
    public static final String PAGE = "page";
    /**
     * 每页显示记录数
     */
    public static final String LIMIT = "limit";
    /**
     * 排序字段
     */
    public static final String ORDER_FIELD = "sidx";
    /**
     * 排序方式
     */
    public static final String ORDER = "order";
    /**
     *  升序
     */
    public static final String ASC = "asc";

    //文件上传临时路径
    public static final String MULTIPART_TEMP_PATH = "./res/temp";
    //授权文件路径
    public static final String MAC_DOWN_PATH = "./res/download/auth";
    //模板下载路径
    public static final String TEMP_DOWN_PATH = "./res/download/temp";
    //设备配置下载路径
    public static final String DEVCONFIG_DOWN_PATH = "./res/download/devconfig";
    //测点图片上传路径
    public static final String MENU_PIC_UPLOAD_PATH = "./res/upload/pic/menu";
    //小区图标上传路径
    public static final String WORK_PIC_UPLOAD_PATH = "./res/upload/pic/work";
    //excel下载路径
    public static final String XLS_DOWNLOAD_PATH = "./res/download/xls";

    /**
     * 系统授权用salt
     */
    public static final String AUTH_SALT = "reason-framework-auth-salt";

    /**
     * 身份证、手机号、家庭住址 加密用key
     */
    public static final String KEY = "reason-framework-aes-key";

    /**
     * 系统字典
     */
    public static final String DIC_SORT_IPLIST = "iplist";
    public static final String DIC_KEY_WHITE_LIST = "white_list";
    public static final String DIC_KEY_BLACK_LIST = "black_list";

    /**
     * 系统参数
     */
    //口令变更等通用参数见 PARAM_* 常量（短信/企业微信/OSS 等公司私有参数已随模块移除）

    /**
     * Redis 相关key
     */
    //系统参数 List<SysParamEntity> 对象存放
    public static final String REDIS_SYS_PARAM = "redis_sys_param";
    //字典 黑白名单配置
    public static final String REDIS_SYS_DIC_IPLIST = "redis_sys_dic_iplist";


    //登录短信验证码验证  1-开启 2-关闭  默认关闭（短信验证码能力已移除，保留常量语义占位）
    public static final String PARAM_CODE_SMS_VERIFY = "code_sms_verify";
    //登录企业微信验证码验证  1-开启 2-关闭  默认关闭（企业微信验证码能力已移除，保留常量语义占位）
    public static final String PARAM_CODE_QYWEIXIN_VERIFY = "code_qyweixin_verify";
    //口令最大尝试次数，超过则限时锁定账号  0-不做限制
    public static final String PARAM_ATTEMPT_LIMIT = "attempt_limit";
    //账号限时锁定时间（单位：分钟） 默认5分钟
    public static final String PARAM_LOCK_TIME = "lock_time";
    //口令定期变更  1-强制变更 2-不强制，只提醒
    public static final String PARAM_CHANGE_FORCE = "change_force";
    //口令变更时限（单位：天） 默认 30天
    public static final String PARAM_CHANGE_LIMIT = "change_limit";

    /**
	 * 菜单类型
	 * 
	 * @author chenshun
	 * @email sunlightcs@gmail.com
	 * @date 2016年11月15日 下午1:24:29
	 */
    public enum MenuType {
        /**
         * 目录
         */
    	CATALOG(0),
        /**
         * 菜单
         */
        MENU(1),
        /**
         * 按钮
         */
        BUTTON(2);

        private int value;

        MenuType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }


    /**
     * 定时任务状态
     * 
     * @author chenshun
     * @email sunlightcs@gmail.com
     * @date 2016年12月3日 上午12:07:22
     */
    public enum ScheduleStatus {
        /**
         * 正常
         */
    	NORMAL(0),
        /**
         * 暂停
         */
    	PAUSE(1);

        private int value;

        ScheduleStatus(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }

}
