-- ============================================================
-- 06-barrier-batch-scale.sql（升降杆样例：批次2 量级适配）
-- 执行时机：05 阶段0 收尾之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) device_alarm 增索引 idx_device_no_type_time (device_no, alarm_type, alarm_createtime)
--      ——告警 raise 的 DB 时间窗查重兜底（0.8：select count 按 设备+类型+窗口 过滤）
--        全条件命中；原 idx_device_handled/idx_type_time 均不覆盖该条件组合，
--        50 台洪峰下查重走全表扫描是真实慢查询面（批次2 B7）
-- 出处：批次2 需求规格 §3 B7 / §4 C3
-- ============================================================

-- ---------- 1. 告警查重索引（幂等：STATISTICS 判存在再 ALTER，05 风格） ----------
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm'
               AND INDEX_NAME = 'idx_device_no_type_time');
SET @ddl := IF(@idx = 0,
  'ALTER TABLE `device_alarm` ADD KEY `idx_device_no_type_time` (`device_no`,`alarm_type`,`alarm_createtime`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
