-- ============================================================
-- 05-barrier-stage0-rest.sql（升降杆样例：阶段0 收尾 0.2-0.8）
-- 执行时机：04 协议v2 之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) device_manual_hold 手动保持期落库表（0.8：Redis 丢失后从 DB 重建，人在杆下不被抢控）
--   2) device_alarm 增处理人/处理时间列（0.4 告警确认闭环）
--   3) device_record 增 device_secret（0.5 per-device HMAC 密钥；存量设备随机生成——动态值不进仓库）
--   4) sys_menu 207 告警处理（device:alarm:handle）+ role_id=2 授权
-- ============================================================

-- ---------- 1. 手动保持期表 ----------
CREATE TABLE IF NOT EXISTS `device_manual_hold` (
  `device_no` varchar(32) NOT NULL COMMENT '设备编号（一条设备最多一条活动保持期）',
  `user_id` bigint NOT NULL COMMENT '操作人（sys_user.user_id）',
  `expire_time` bigint NOT NULL COMMENT '保持期到期时间戳(秒)',
  `create_time` bigint NOT NULL COMMENT '创建时间戳(秒)',
  PRIMARY KEY (`device_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手动保持期（0.8：Redis 易失态的 DB 双写重建源）';

-- ---------- 2. 告警处理列 ----------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm'
              AND COLUMN_NAME = 'alarm_handler');
SET @ddl1 := IF(@c1 = 0,
  'ALTER TABLE `device_alarm` ADD COLUMN `alarm_handler` bigint DEFAULT NULL COMMENT ''处理人(sys_user.user_id,0.4告警确认闭环)'' AFTER `alarm_handled`',
  'SELECT 1');
PREPARE stmt1 FROM @ddl1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm'
              AND COLUMN_NAME = 'alarm_handled_time');
SET @ddl2 := IF(@c2 = 0,
  'ALTER TABLE `device_alarm` ADD COLUMN `alarm_handled_time` bigint DEFAULT NULL COMMENT ''处理时间戳(毫秒, D-H)'' AFTER `alarm_handler`',
  'SELECT 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- ---------- 3. 设备密钥列（0.5 per-device HMAC；存量设备随机生成，动态值不进仓库明文） ----------
SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_record'
              AND COLUMN_NAME = 'device_secret');
SET @ddl3 := IF(@c3 = 0,
  'ALTER TABLE `device_record` ADD COLUMN `device_secret` varchar(64) DEFAULT NULL COMMENT ''设备HMAC密钥(0.5 per-device凭证,平台登记时生成,仓库零明文)'' AFTER `device_last_event_seq`',
  'SELECT 1');
PREPARE stmt3 FROM @ddl3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

UPDATE `device_record` SET `device_secret` = REPLACE(UUID(), '-', '')
WHERE `device_secret` IS NULL OR `device_secret` = '';

-- ---------- 4. 菜单按钮权限（207 告警处理，挂 200 设备台账菜单下） ----------
DELETE FROM `sys_menu` WHERE `menu_id` IN (207);
INSERT INTO `sys_menu`
  (`menu_id`, `menu_origin`, `menu_type`, `menu_name`, `menu_perms`,
   `menu_icon`, `menu_pic`, `menu_def_pic`, `menu_url`, `menu_page`,
   `menu_fid`, `menu_fids`, `menu_ordernum`,
   `menu_createtime`, `menu_updatetime`, `menu_status`)
VALUES
  (207, 1, 2, '告警处理', 'device:alarm:handle', NULL, NULL, NULL, NULL, NULL,
   200, '0,200', 0, unix_timestamp(now()), unix_timestamp(now()), 0);

DELETE FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` IN (207);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_id` IN (207);

-- ---------- 5. 列注释同步（0.2-0.8 新增枚举态回写；幂等：MODIFY 每次执行仅改注释） ----------
ALTER TABLE `device_command_log`
  MODIFY COLUMN `command_status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-已下发待到位 1-已到位 2-下发失败 3-重试超限转故障 4-执行失败(设备故障) 5-被更新指令取代(0.2代际裁决)';
ALTER TABLE `device_alarm`
  MODIFY COLUMN `alarm_type` tinyint NOT NULL COMMENT '类型：1-指令重试超限 2-设备离线 3-状态对账不一致 4-设备故障上报 5-自动校正连续失败(0.3熔断) 6-动作卡死未到位(0.4巡检)';
