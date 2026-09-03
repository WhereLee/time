/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 50717
 Source Host           : localhost:3306
 Source Schema         : faster

 Target Server Type    : MySQL
 Target Server Version : 50717
 File Encoding         : 65001

 Date: 18/02/2021 16:25:19
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for QRTZ_BLOB_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_BLOB_TRIGGERS`;
CREATE TABLE `QRTZ_BLOB_TRIGGERS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `BLOB_DATA` blob NULL DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
  INDEX `SCHED_NAME`(`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
  CONSTRAINT `QRTZ_BLOB_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_CALENDARS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_CALENDARS`;
CREATE TABLE `QRTZ_CALENDARS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `CALENDAR_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `CALENDAR` blob NOT NULL,
  PRIMARY KEY (`SCHED_NAME`, `CALENDAR_NAME`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_CRON_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_CRON_TRIGGERS`;
CREATE TABLE `QRTZ_CRON_TRIGGERS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `CRON_EXPRESSION` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TIME_ZONE_ID` varchar(80) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
  CONSTRAINT `QRTZ_CRON_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_FIRED_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_FIRED_TRIGGERS`;
CREATE TABLE `QRTZ_FIRED_TRIGGERS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `ENTRY_ID` varchar(95) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `INSTANCE_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `FIRED_TIME` bigint(13) NOT NULL,
  `SCHED_TIME` bigint(13) NOT NULL,
  `PRIORITY` int(11) NOT NULL,
  `STATE` varchar(16) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `JOB_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `JOB_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `IS_NONCONCURRENT` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `REQUESTS_RECOVERY` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`, `ENTRY_ID`) USING BTREE,
  INDEX `IDX_QRTZ_FT_TRIG_INST_NAME`(`SCHED_NAME`, `INSTANCE_NAME`) USING BTREE,
  INDEX `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY`(`SCHED_NAME`, `INSTANCE_NAME`, `REQUESTS_RECOVERY`) USING BTREE,
  INDEX `IDX_QRTZ_FT_J_G`(`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_FT_JG`(`SCHED_NAME`, `JOB_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_FT_T_G`(`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_FT_TG`(`SCHED_NAME`, `TRIGGER_GROUP`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_JOB_DETAILS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_JOB_DETAILS`;
CREATE TABLE `QRTZ_JOB_DETAILS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `JOB_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `JOB_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `DESCRIPTION` varchar(250) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `JOB_CLASS_NAME` varchar(250) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `IS_DURABLE` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `IS_NONCONCURRENT` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `IS_UPDATE_DATA` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `REQUESTS_RECOVERY` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `JOB_DATA` blob NULL DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_J_REQ_RECOVERY`(`SCHED_NAME`, `REQUESTS_RECOVERY`) USING BTREE,
  INDEX `IDX_QRTZ_J_GRP`(`SCHED_NAME`, `JOB_GROUP`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_LOCKS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_LOCKS`;
CREATE TABLE `QRTZ_LOCKS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `LOCK_NAME` varchar(40) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  PRIMARY KEY (`SCHED_NAME`, `LOCK_NAME`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_PAUSED_TRIGGER_GRPS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_PAUSED_TRIGGER_GRPS`;
CREATE TABLE `QRTZ_PAUSED_TRIGGER_GRPS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  PRIMARY KEY (`SCHED_NAME`, `TRIGGER_GROUP`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_SCHEDULER_STATE
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_SCHEDULER_STATE`;
CREATE TABLE `QRTZ_SCHEDULER_STATE`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `INSTANCE_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `LAST_CHECKIN_TIME` bigint(13) NOT NULL,
  `CHECKIN_INTERVAL` bigint(13) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`, `INSTANCE_NAME`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_SIMPLE_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_SIMPLE_TRIGGERS`;
CREATE TABLE `QRTZ_SIMPLE_TRIGGERS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `REPEAT_COUNT` bigint(7) NOT NULL,
  `REPEAT_INTERVAL` bigint(12) NOT NULL,
  `TIMES_TRIGGERED` bigint(10) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
  CONSTRAINT `QRTZ_SIMPLE_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_SIMPROP_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_SIMPROP_TRIGGERS`;
CREATE TABLE `QRTZ_SIMPROP_TRIGGERS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `STR_PROP_1` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `STR_PROP_2` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `STR_PROP_3` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `INT_PROP_1` int(11) NULL DEFAULT NULL,
  `INT_PROP_2` int(11) NULL DEFAULT NULL,
  `LONG_PROP_1` bigint(20) NULL DEFAULT NULL,
  `LONG_PROP_2` bigint(20) NULL DEFAULT NULL,
  `DEC_PROP_1` decimal(13, 4) NULL DEFAULT NULL,
  `DEC_PROP_2` decimal(13, 4) NULL DEFAULT NULL,
  `BOOL_PROP_1` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `BOOL_PROP_2` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
  CONSTRAINT `QRTZ_SIMPROP_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for QRTZ_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_TRIGGERS`;
CREATE TABLE `QRTZ_TRIGGERS`  (
  `SCHED_NAME` varchar(120) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `JOB_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `JOB_GROUP` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `DESCRIPTION` varchar(250) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `NEXT_FIRE_TIME` bigint(13) NULL DEFAULT NULL,
  `PREV_FIRE_TIME` bigint(13) NULL DEFAULT NULL,
  `PRIORITY` int(11) NULL DEFAULT NULL,
  `TRIGGER_STATE` varchar(16) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `TRIGGER_TYPE` varchar(8) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `START_TIME` bigint(13) NOT NULL,
  `END_TIME` bigint(13) NULL DEFAULT NULL,
  `CALENDAR_NAME` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `MISFIRE_INSTR` smallint(2) NULL DEFAULT NULL,
  `JOB_DATA` blob NULL DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_T_J`(`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_T_JG`(`SCHED_NAME`, `JOB_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_T_C`(`SCHED_NAME`, `CALENDAR_NAME`) USING BTREE,
  INDEX `IDX_QRTZ_T_G`(`SCHED_NAME`, `TRIGGER_GROUP`) USING BTREE,
  INDEX `IDX_QRTZ_T_STATE`(`SCHED_NAME`, `TRIGGER_STATE`) USING BTREE,
  INDEX `IDX_QRTZ_T_N_STATE`(`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`, `TRIGGER_STATE`) USING BTREE,
  INDEX `IDX_QRTZ_T_N_G_STATE`(`SCHED_NAME`, `TRIGGER_GROUP`, `TRIGGER_STATE`) USING BTREE,
  INDEX `IDX_QRTZ_T_NEXT_FIRE_TIME`(`SCHED_NAME`, `NEXT_FIRE_TIME`) USING BTREE,
  INDEX `IDX_QRTZ_T_NFT_ST`(`SCHED_NAME`, `TRIGGER_STATE`, `NEXT_FIRE_TIME`) USING BTREE,
  INDEX `IDX_QRTZ_T_NFT_MISFIRE`(`SCHED_NAME`, `MISFIRE_INSTR`, `NEXT_FIRE_TIME`) USING BTREE,
  INDEX `IDX_QRTZ_T_NFT_ST_MISFIRE`(`SCHED_NAME`, `MISFIRE_INSTR`, `NEXT_FIRE_TIME`, `TRIGGER_STATE`) USING BTREE,
  INDEX `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP`(`SCHED_NAME`, `MISFIRE_INSTR`, `NEXT_FIRE_TIME`, `TRIGGER_GROUP`, `TRIGGER_STATE`) USING BTREE,
  CONSTRAINT `QRTZ_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) REFERENCES `QRTZ_JOB_DETAILS` (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;



-- ----------------------------
-- Table structure for schedule_job  定时任务信息
-- ----------------------------
DROP TABLE IF EXISTS `schedule_job`;
CREATE TABLE `schedule_job`  (
  `job_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务id',
  `job_bean` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'spring bean名称',
  `job_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务名称',
  `job_params` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '参数',
  `job_cron` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'cron表达式',
  `job_state` tinyint(1) NULL DEFAULT 0 COMMENT '任务状态  0：正常  1：暂停',
  `job_comment` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '说明、备注',
  `job_createtime` bigint(20) NULL DEFAULT NULL COMMENT '创建时间戳，单位秒',
  `job_updatetime` bigint(20) NULL DEFAULT NULL COMMENT '更新时间戳，单位秒',
  `job_status` bigint(20) NULL DEFAULT 0 COMMENT '有效状态 0-有效 >0 无效',
  PRIMARY KEY (`job_id`) USING BTREE,
  UNIQUE INDEX `u_jobbean`(`job_bean`, `job_status`) USING BTREE,
  UNIQUE INDEX `u_jobname`(`job_name`, `job_status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for schedule_job_log  定时任务执行日志
-- ----------------------------
DROP TABLE IF EXISTS `schedule_job_log`;
CREATE TABLE `schedule_job_log`  (
  `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务日志id',
  `job_id` bigint(20) NULL DEFAULT NULL COMMENT '任务id',
  `job_bean` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'spring bean名称',
  `job_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务名称',
  `log_state` tinyint(1) NULL DEFAULT 0 COMMENT '执行状态    0：成功    1：失败',
  `log_error` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败信息',
  `log_duration` bigint(20) NULL DEFAULT NULL COMMENT '执行时长 单位 毫秒',
  `log_createtime` bigint(20) NULL DEFAULT NULL COMMENT '创建时间戳，单位秒',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `job_id`(`job_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务日志' ROW_FORMAT = Compact;


-- ----------------------------
-- Table structure for sys_dictionary	字典表
-- ----------------------------
DROP TABLE IF EXISTS `sys_dictionary`;
CREATE TABLE `sys_dictionary`  (
  `dic_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键 自增',
  `dic_sort` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '分类  如 IP黑白名单 iplist 等',
  `dic_key` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT 'Key 后端生成',
  `dic_value` varchar(2048) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '值',
  `dic_remark` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '说明、备注',
  `dic_creator` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `dic_createtime` bigint(20) NULL DEFAULT NULL COMMENT '创建时间戳，单位秒',
  `dic_updatetime` bigint(20) NULL DEFAULT NULL COMMENT '更新时间戳，单位秒',
  `dic_status` bigint(20) NULL DEFAULT 0 COMMENT '状态 0-有效 >0 无效 默认0',
  PRIMARY KEY (`dic_id`) USING BTREE,
  UNIQUE INDEX `uni_sort_key`(`dic_sort`, `dic_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;


-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `config_title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `config_value` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;


-- ----------------------------
-- Table structure for sys_menu  菜单信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `menu_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `menu_origin` tinyint(1) NULL DEFAULT 1 COMMENT '1-WEB端使用的菜/按钮 2-APP端使用的菜/按钮',
  `menu_type` tinyint(1) NULL DEFAULT NULL COMMENT '0-目录 1-菜单 2-按钮',
  `menu_name` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '菜单/按钮名字，名字允许重复，可以是中文',
  `menu_perms` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '授权(多个用逗号分隔，如：user:list,user:create)',
  `menu_icon` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '菜单/按钮的图标',
  `menu_pic` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '菜单图片地址',
  `menu_def_pic` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '菜单默认图片地址',
  `menu_url` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '菜单URL',
  `menu_page` varchar(512) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT 'Vue实际使用的路径',
  `menu_fid` bigint(20) NULL DEFAULT NULL COMMENT '父级id，没有则是0',
  `menu_fids` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '父级ids 比如：0,1,11',
  `menu_ordernum` int(11) NULL DEFAULT 0 COMMENT '菜单的排序',
  `menu_createtime` bigint(20) NULL DEFAULT NULL COMMENT '创建时间戳，单位秒',
  `menu_updatetime` bigint(20) NULL DEFAULT NULL COMMENT '更新时间戳，单位秒',
  `menu_status` bigint(20) NULL DEFAULT 0 COMMENT '0-正常，1-删除',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for sys_role  角色信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `role_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_name` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '角色名称',
  `role_comment` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '角色描述，备注',
  `role_creator` bigint(20) NULL DEFAULT NULL COMMENT '创建人 对应user表id ',
  `role_createtime` bigint(20) NULL DEFAULT NULL COMMENT '角色创建时间戳，单位秒',
  `role_updatetime` bigint(20) NULL DEFAULT NULL COMMENT '角色修改时间戳，单位秒',
  `role_status` bigint(20) NULL DEFAULT 0 COMMENT '0-有效，>0-无效，表示删除',
  PRIMARY KEY (`role_id`) USING BTREE,
  UNIQUE INDEX `u_rolename`(`role_name`, `role_status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for sys_role_menu  角色菜单信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint(20) NULL DEFAULT NULL COMMENT '角色ID，sys_role表',
  `menu_id` bigint(20) NULL DEFAULT NULL COMMENT '菜单ID，sys_menu表',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;


-- ----------------------------
-- Table structure for sys_user  用户信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_name` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '用户名',
  `user_password` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '用户从后台页面登录时候，输入的密码需要经过MD5处理，才可以传给后台，后台使用Sha256Hash\r\n+salt 加密',
  `user_salt` varchar(20) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '盐值，系统自动产生',
  `user_realname` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '用户的真实名字',
  `user_email` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `user_phone` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '电话号码',
  `user_qyweixin_id` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '企业微信用户ID',
  `user_logintime` bigint(20) NULL DEFAULT NULL COMMENT '最后一次登录时间戳',
  `user_loginip` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '最后一次登录IP',
  `user_pwd_changetime` bigint(20) NULL DEFAULT NULL COMMENT '密码变更时间戳 秒  初始为第一次登录时间',
  `user_creator` bigint(20) NULL DEFAULT NULL COMMENT '创建人 对应user表id ',
  `user_createtime` bigint(20) NULL DEFAULT NULL COMMENT '创建时的时间戳，单位为秒',
  `user_updatetime` bigint(20) NULL DEFAULT NULL COMMENT '更新时的时间戳，单位为秒',
  `user_recycle` tinyint(1) NULL DEFAULT 0 COMMENT '0-正常 1-回收，不能登录',
  `user_status` bigint(20) NULL DEFAULT 0 COMMENT '0-正常，>0-删除',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `u_username`(`user_name`, `user_status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for sys_user_role  用户角色信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '用户ID sys_user表',
  `role_id` bigint(20) NULL DEFAULT NULL COMMENT '角色ID sys_role表',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for sys_user_token  用户token信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_token`;
CREATE TABLE `sys_user_token`  (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `token` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT 'token',
  `expiretime` bigint(20) NULL DEFAULT NULL COMMENT '过期时间戳，单位秒',
  `updatetime` bigint(20) NULL DEFAULT NULL COMMENT '更新时间戳，单位秒',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;


-- ----------------------------
-- Table structure for sys_param  系统参数信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_param`;
CREATE TABLE `sys_param`  (
  `param_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `param_name` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '参数的名称，不可修改，显示到页面',
  `param_key` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '参数的Key，不可修改，后台调用的时候用',
  `param_value` varchar(2048) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '参数的值',
  `param_comment` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '说明、备注',
  `param_createtime` bigint(20) NULL DEFAULT NULL COMMENT '创建时间戳',
  `param_updatetime` bigint(20) NULL DEFAULT NULL COMMENT '更新时间戳',
  `param_recycle` tinyint(1) NULL DEFAULT 0 COMMENT '参数开放标志 0-开放，1-关闭',
  PRIMARY KEY (`param_id`) USING BTREE,
  UNIQUE INDEX `u_paramkey`(`param_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;


SET FOREIGN_KEY_CHECKS = 1;


-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (111, 1, 0, '系统管理', NULL, NULL, NULL, NULL, NULL, '/menu', 0, '0', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (112, 1, 1, '角色管理', NULL, NULL, NULL, NULL, NULL, '/role', 111, '0,111', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (113, 1, 2, '查询', 'sys:role:list,sys:role:select,sys:role:info', NULL, NULL, NULL, NULL, NULL, 112, '0,111,112', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (114, 1, 2, '新增', 'sys:role:save,sys:menu:select,biz:region:select', NULL, NULL, NULL, NULL, NULL, 112, '0,111,112', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (115, 1, 2, '修改', 'sys:role:update,sys:role:info,sys:menu:select,biz:region:select', NULL, NULL, NULL, NULL, NULL, 112, '0,111,112', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (116, 1, 2, '删除', 'sys:role:delete', NULL, NULL, NULL, NULL, NULL, 112, '0,111,112', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (117, 1, 2, '详情', 'sys:role:info,sys:role:detail', NULL, NULL, NULL, NULL, '/menu', 112, '0,111,112', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (118, 1, 1, '用户管理', NULL, NULL, NULL, NULL, NULL, '/user', 111, '0,111', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (119, 1, 2, '查询', 'sys:user:list,sys:user:select,sys:user:info', NULL, NULL, NULL, NULL, NULL, 118, '0,111,118', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (120, 1, 2, '新增', 'sys:user:save,sys:role:select', NULL, NULL, NULL, NULL, NULL, 118, '0,111,118', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (121, 1, 2, '修改', 'sys:user:update,sys:user:info,sys:role:select', NULL, NULL, NULL, NULL, NULL, 118, '0,111,118', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (122, 1, 2, '删除', 'sys:user:delete', NULL, NULL, NULL, NULL, NULL, 118, '0,111,118', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (123, 1, 2, '锁定', 'sys:user:close', NULL, NULL, NULL, NULL, NULL, 118, '0,111,118', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (124, 1, 2, '解锁', 'sys:user:open', NULL, NULL, NULL, NULL, NULL, 118, '0,111,118', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (125, 1, 2, '重置密码', 'sys:user:reset-password', NULL, NULL, NULL, NULL, NULL, 118, '0,111,118', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (126, 1, 1, '菜单管理', NULL, NULL, NULL, NULL, NULL, '/menu', 111, '0,111', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (127, 1, 2, '查询', 'sys:menu:list,sys:menu:select,sys:menu:info', NULL, NULL, NULL, NULL, NULL, 126, '0,111,126', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (128, 1, 2, '新增', 'sys:menu:save,sys:menu:select', NULL, NULL, NULL, NULL, NULL, 126, '0,111,126', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (129, 1, 2, '修改', 'sys:menu:update,sys:menu:select,sys:menu:info', NULL, NULL, NULL, NULL, NULL, 126, '0,111,126', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (130, 1, 2, '删除', 'sys:menu:delete', NULL, NULL, NULL, NULL, NULL, 126, '0,111,126', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (136, 1, 1, '系统参数', NULL, NULL, NULL, NULL, NULL, '/xtcs', 111, '0,111', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (137, 1, 2, '查询', 'sys:param:list,sys:param:info', NULL, NULL, NULL, NULL, NULL, 136, '0,111,136', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (138, 1, 2, '新增', 'sys:param:save', NULL, NULL, NULL, NULL, NULL, 136, '0,111,136', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (139, 1, 2, '修改', 'sys:param:update,sys:param:info', NULL, NULL, NULL, NULL, NULL, 136, '0,111,136', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (140, 1, 2, '关闭', 'sys:param:close', NULL, NULL, NULL, NULL, NULL, 136, '0,111,136', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (141, 1, 2, '开放', 'sys:param:open', NULL, NULL, NULL, NULL, NULL, 136, '0,111,136', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (142, 1, 0, '系统日志', NULL, NULL, NULL, NULL, NULL, '/menu', 0, '0', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (143, 1, 1, '操作日志', NULL, NULL, NULL, NULL, NULL, '/sys', 142, '0,142', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (144, 1, 2, '查询', 'sys:log:list,sys:log:info', NULL, NULL, NULL, NULL, NULL, 143, '0,142,143', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (147, 1, 1, '异常日志', NULL, NULL, NULL, NULL, NULL, '/errlog', 142, '0,142', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (148, 1, 2, '查询', 'sys:errorlog:list,sys:errorlog:info', NULL, NULL, NULL, NULL, NULL, 147, '0,142,147', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (149, 1, 0, '定时任务', NULL, NULL, NULL, NULL, NULL, '/menu', 0, '0', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (150, 1, 1, '定时任务', NULL, NULL, NULL, NULL, NULL, '/timedTask', 149, '0,149', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (151, 1, 2, '查询', 'sys:schedule:list,sys:schedule:info', NULL, NULL, NULL, NULL, NULL, 150, '0,149,150', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (152, 1, 2, '新增', 'sys:schedule:save', NULL, NULL, NULL, NULL, NULL, 150, '0,149,150', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (153, 1, 2, '修改', 'sys:schedule:update,sys:schedule:info', NULL, NULL, NULL, NULL, NULL, 150, '0,149,150', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (154, 1, 2, '删除', 'sys:schedule:delete', NULL, NULL, NULL, NULL, NULL, 150, '0,149,150', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (155, 1, 2, '立即执行', 'sys:schedule:run', NULL, NULL, NULL, NULL, NULL, 150, '0,149,150', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (156, 1, 2, '暂停', 'sys:schedule:pause', NULL, NULL, NULL, NULL, NULL, 150, '0,149,150', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (157, 1, 2, '恢复', 'sys:schedule:resume', NULL, NULL, NULL, NULL, NULL, 150, '0,149,150', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (158, 1, 1, '任务日志', NULL, NULL, NULL, NULL, NULL, '/Joblog', 149, '0,149', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (159, 1, 2, '查询', 'sys:schedulelog:list,sys:schedulelog:info', NULL, NULL, NULL, NULL, NULL, 158, '0,149,158', 0, unix_timestamp(now()), unix_timestamp(now()), 0);

INSERT INTO `sys_menu` VALUES (160, 1, 1, 'IP黑白名单', NULL, NULL, NULL, NULL, NULL, '/whiteAndBlackList', 111, '0,111', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (161, 1, 2, '查询', 'sys:iplist:info', NULL, NULL, NULL, NULL, NULL, 160, '0,111,160', 0, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_menu` VALUES (162, 1, 2, '修改', 'sys:iplist:update,sys:iplist:info', NULL, NULL, NULL, NULL, NULL, 160, '0,111,160', 0, unix_timestamp(now()), unix_timestamp(now()), 0);


-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '开发员', NULL, -1, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_role` VALUES (2, '系统管理员', NULL, 1, unix_timestamp(now()), unix_timestamp(now()), 0);


-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'dev@kf', '9cc3bc4ef4d9e79e0d55e7b2f76cf26a8c8a7f8d625fc04a737f0ea7d39aa550', 'mrspwDtoYvtckE3cHfxl', '开发员', NULL, NULL, NULL, NULL, NULL, NULL, -1, unix_timestamp(now()), unix_timestamp(now()), 0, 0);
INSERT INTO `sys_user` VALUES (2, 'adminManager', '16de761762f79ffe34607bfe0d02995024517a2c38ddbbd2787b893ec946efef', 'CYiKIzx4410U9yaBPBHE', '系统管理员', NULL, NULL, NULL, NULL, NULL, NULL, 1, unix_timestamp(now()), unix_timestamp(now()), 0, 0);


-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1, 1);
INSERT INTO `sys_user_role` VALUES (2, 2, 2);

-- ----------------------------
-- Records of sys_dictionary
-- ----------------------------
INSERT INTO `sys_dictionary` VALUES (1, 'iplist', 'white_list', NULL, 'IP白名单 多个IP英文逗号分隔 空时允许所有IP登录（黑名单除外），不为空时列表内IP才能登录', -1, unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_dictionary` VALUES (2, 'iplist', 'black_list', NULL, 'IP黑名单 多个IP英文逗号分隔 列表内IP禁止登录', -1, unix_timestamp(now()), unix_timestamp(now()), 0);



-- ----------------------------
-- Records of sys_param
-- ----------------------------
INSERT INTO `sys_param` VALUES (3, '口令最大尝试次数', 'attempt_limit', '0', '口令最大尝试次数，超过则限时锁定账号  0-不做限制 默认不做限制', unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_param` VALUES (4, '账号限时锁定时间', 'lock_time', '5', '账号限时锁定时间（单位：分钟） 默认5分钟', unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_param` VALUES (5, '口令定期变更', 'change_force', '2', '口令定期变更  1-强制变更 2-提醒变更  默认不强制', unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_param` VALUES (6, '口令变更时限', 'change_limit', '30', '口令变更时限（单位：天） 默认 30天', unix_timestamp(now()), unix_timestamp(now()), 0);

INSERT INTO `sys_param` VALUES (7, '图片上传大小限制', 'pic_upload_limit', '5', '图片上传大小限制（单位：MB） 默认5MB', unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_param` VALUES (8, '音频上传大小限制', 'audio_upload_limit', '100', '音频上传大小限制（单位：MB） 默认100MB', unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_param` VALUES (9, '视频上传大小限制', 'video_upload_limit', '1024', '视频上传大小限制（单位：MB） 默认1024MB', unix_timestamp(now()), unix_timestamp(now()), 0);
INSERT INTO `sys_param` VALUES (10, '分片上传分片大小', 'part_size', '10', '分片上传分片大小（单位：MB） 默认10M', unix_timestamp(now()), unix_timestamp(now()), 0);


-- ----------------------------
-- Table structure for sys_log  系统操作日志（原框架使用 MongoDB 按月度表存储，改造后统一落 MySQL）
-- ----------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `log_type` int NULL DEFAULT NULL COMMENT '日志类型 1-WEB端 2-APP端',
  `log_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求URL',
  `log_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '请求参数',
  `log_state` int NULL DEFAULT NULL COMMENT '执行结果 0-成功 1-失败',
  `log_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '执行信息',
  `log_return` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '返回结果',
  `log_error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '异常信息',
  `log_module` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '模块',
  `log_func` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '功能',
  `log_operation` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作描述',
  `log_method` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求方式',
  `log_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '登录IP',
  `log_browser` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '浏览器',
  `log_duration` bigint NULL DEFAULT NULL COMMENT '执行时长（毫秒）',
  `log_creator` bigint NULL DEFAULT NULL COMMENT '操作人ID',
  `log_creator_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人',
  `log_openid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'OPENID',
  `log_nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `log_profile` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
  `log_mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `log_createtime` bigint NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`) USING BTREE,
  KEY `idx_log_createtime` (`log_createtime`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统操作日志' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 初始化：系统管理员角色(role_id=2) 关联全部启用菜单（必须在 sys_menu 数据之后执行）
-- ----------------------------
-- 系统管理员角色(role_id=2) 关联全部启用菜单（保证初始账号拥有完整权限）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_status` = 0;
