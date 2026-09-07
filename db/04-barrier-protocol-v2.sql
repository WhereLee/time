-- ============================================================
-- 04-barrier-protocol-v2.sql（升降杆样例：事件协议 v2——序守卫列，0.1）
-- 执行时机：03 全闭环之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) device_record 增列 device_last_boot_id / device_last_event_seq
--      ——台账序守卫：同一 bootId（设备重启代际）内只接受单调递增事件，
--        重放/乱序/陈旧覆盖在此被拒；bootId 变化=新代际，接受并重置基线
-- 契约依据：contracts/PROTOCOL-V2.md §3.3
-- ============================================================

SET @col1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_record'
                AND COLUMN_NAME = 'device_last_boot_id');
SET @col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_record'
                AND COLUMN_NAME = 'device_last_event_seq');

SET @ddl1 := IF(@col1 = 0,
  'ALTER TABLE `device_record` ADD COLUMN `device_last_boot_id` varchar(64) DEFAULT NULL COMMENT ''最近事件bootId(设备重启代际,协议v2序守卫)'' AFTER `device_updatetime`',
  'SELECT 1');
SET @ddl2 := IF(@col2 = 0,
  'ALTER TABLE `device_record` ADD COLUMN `device_last_event_seq` bigint DEFAULT NULL COMMENT ''最近事件序号(协议v2序守卫:同bootId内单调递增,重放/乱序被拒)'' AFTER `device_last_boot_id`',
  'SELECT 1');

PREPARE stmt1 FROM @ddl1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
