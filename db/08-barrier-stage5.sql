-- ============================================================
-- 08-barrier-stage5.sql（升降杆样例：批次5 横切治理）
-- 执行时机：07 之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) 表保留清理任务注册（job_id=203，每日 03:30 清理 90 天前数据）
--   2) device_alarm / device_command_log 补"创建时间"单列索引（清理扫描走索引，防全表扫）
--   3) 认证前置（T19）：attempt_limit 种子 0 -> 5 的数据迁移
--      （对已部署库：仅当仍是种子值 0 时更新——已显式配置过的库不覆盖）
--
-- 设计说明：
--   - 清理任务（D-G）分批 LIMIT 删除（每批 500 行独立短事务），单表批数上限 200 防意外巨量；
--     参数在 yml reason.retention.*（days/batch-size/max-batches）；
--   - 索引：现有 idx_type_time(alarm_type,alarm_createtime) / idx_status_time(command_status,command_createtime)
--     首列均非时间列，清理条件 alarm_createtime</command_createtime 无法利用——补单列索引；
--   - attempt_limit 迁移后需清理 Redis 参数缓存（key: redis_sys_param，下一次登录自动重建）：
--     本地/CI 重跑建库不受影响（种子已直接改为 5）；仅存量已部署库需要手动删缓存键。
-- ============================================================

-- ---------- 1. 表保留清理任务注册（固定 job_id 203，先删后插幂等） ----------
DELETE FROM `schedule_job` WHERE `job_id` = 203;
INSERT INTO `schedule_job`
  (`job_id`, `job_bean`, `job_name`, `job_params`, `job_cron`,
   `job_state`, `job_comment`, `job_createtime`, `job_updatetime`, `job_status`)
VALUES
  (203, 'dataRetentionCleanTask', '表保留清理（90天）', NULL, '0 30 3 * * ?',
   0, '每日03:30：device_alarm/device_command_log/sys_log 物理清理 90 天前数据（分批 LIMIT 短事务，防表膨胀）',
   unix_timestamp(now()), unix_timestamp(now()), 0);

-- ---------- 2. 清理扫描索引（幂等：存在即跳过） ----------
SET @i1 := (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm'
              AND INDEX_NAME = 'idx_createtime');
SET @ddl_i1 := IF(@i1 = 0,
  'ALTER TABLE `device_alarm` ADD INDEX `idx_createtime` (`alarm_createtime`)',
  'SELECT 1');
PREPARE stmt_i1 FROM @ddl_i1; EXECUTE stmt_i1; DEALLOCATE PREPARE stmt_i1;

SET @i2 := (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_command_log'
              AND INDEX_NAME = 'idx_createtime');
SET @ddl_i2 := IF(@i2 = 0,
  'ALTER TABLE `device_command_log` ADD INDEX `idx_createtime` (`command_createtime`)',
  'SELECT 1');
PREPARE stmt_i2 FROM @ddl_i2; EXECUTE stmt_i2; DEALLOCATE PREPARE stmt_i2;

-- ---------- 3. attempt_limit 迁移（T19：仅种子值 0 升级为 5，已显式配置不改） ----------
UPDATE `sys_param` SET `param_value` = '5'
WHERE `param_key` = 'attempt_limit' AND `param_value` = '0';
