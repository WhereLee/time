-- ============================================================
-- 02-barrier-device-record.sql（升降杆样例：设备台账 块1）
-- 执行时机：01 框架基线之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) device_record 设备台账表（"杆的档案"：管理端登记/查看的设备实体）
--   2) 设备台账菜单 + 按钮权限（device:record:list/save + device:command:open/close）
--   3) 系统管理员(role_id=2) 授权关联
--
-- 设计说明（与对象设计对应）：
--   台账只保存"档案 + 最近一次知道的状态"，状态是快照不是真相；
--   真相在设备侧，状态只能由设备上报的事件驱动更新（反馈闭环铁律）。
-- ============================================================

DROP TABLE IF EXISTS `device_record`;
CREATE TABLE `device_record` (
  `device_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_no` varchar(32) NOT NULL COMMENT '设备编号（唯一；管理端登记，后续指令寻址/事件上报按此对齐）',
  `device_name` varchar(64) DEFAULT NULL COMMENT '设备名称（如：东门一号杆）',
  `device_type` tinyint NOT NULL DEFAULT '1' COMMENT '设备类型：1-道闸/升降杆',
  `location` varchar(64) DEFAULT NULL COMMENT '安装位置（如：东门入口）',
  `device_state` tinyint NOT NULL DEFAULT '0' COMMENT '状态快照：0-未接入 1-升起 2-降下 3-动作中 4-故障（登记默认0；只允许设备事件驱动更新）',
  `device_remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `device_creator` bigint DEFAULT NULL COMMENT '登记人 sys_user.user_id',
  `device_createtime` bigint NOT NULL COMMENT '登记时间戳(秒)',
  `device_updatetime` bigint DEFAULT NULL COMMENT '更新时间戳(秒)',
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `u_device_no` (`device_no`) COMMENT '设备编号唯一',
  KEY `idx_type_state` (`device_type`,`device_state`) COMMENT '类型+状态检索'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备台账（管理端登记的设备档案；升降杆样例）';

-- ---------- 2. 菜单/按钮权限（固定 id 200~204，幂等：先删后插） ----------
DELETE FROM `sys_menu` WHERE `menu_id` IN (200, 201, 202, 203, 204);
INSERT INTO `sys_menu`
  (`menu_id`, `menu_origin`, `menu_type`, `menu_name`, `menu_perms`,
   `menu_icon`, `menu_pic`, `menu_def_pic`, `menu_url`, `menu_page`,
   `menu_fid`, `menu_fids`, `menu_ordernum`,
   `menu_createtime`, `menu_updatetime`, `menu_status`)
VALUES
  (200, 1, 1, '设备台账', NULL, NULL, NULL, NULL, NULL, '/device',
   0, '0', 0, unix_timestamp(now()), unix_timestamp(now()), 0),
  (201, 1, 2, '查询', 'device:record:list', NULL, NULL, NULL, NULL, NULL,
   200, '0,200', 0, unix_timestamp(now()), unix_timestamp(now()), 0),
  (202, 1, 2, '新增', 'device:record:save', NULL, NULL, NULL, NULL, NULL,
   200, '0,200', 0, unix_timestamp(now()), unix_timestamp(now()), 0),
  (203, 1, 2, '升起', 'device:command:open', NULL, NULL, NULL, NULL, NULL,
   200, '0,200', 0, unix_timestamp(now()), unix_timestamp(now()), 0),
  (204, 1, 2, '降下', 'device:command:close', NULL, NULL, NULL, NULL, NULL,
   200, '0,200', 0, unix_timestamp(now()), unix_timestamp(now()), 0);

-- ---------- 3. 系统管理员(role_id=2) 授权（幂等） ----------
DELETE FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` IN (200, 201, 202, 203, 204);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_id` IN (200, 201, 202, 203, 204);
