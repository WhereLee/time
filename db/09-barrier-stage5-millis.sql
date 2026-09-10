-- ============================================================
-- 09-barrier-stage5-millis.sql（升降杆样例：批次5 D-H 时间戳毫秒化——破坏性变更，单独迁移）
-- 执行时机：08 之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) 三表六列存量数据迁移：秒值 ×1000 -> 毫秒（幂等条件：值 < 1e10 视为秒值；
--      毫秒值约 1.78e12 远大于该阈值，重复执行不二次放大）
--   2) 六列列注释同步为"毫秒"
--
-- 设计说明（D-H）：
--   - 范围严格限定 device_record / device_command_log / device_alarm 的 createtime/updatetime
--     与 alarm_handled_time（原"秒级与 3s grace 同量级 ±1s 抖动"的隐患窗口消除）；
--   - schedule_*（调度/log 表）/sys_log/manual_hold 保持秒级不变——调度框架与看护判据依赖其秒语义；
--   - grace 等业务窗口语义不变（秒数配置 ×1000 换算为毫秒比较，见各服务实现）；
--   - 代码侧全量换算点：recordPending/markArrivedBySeq/raise/handle/心跳对账/stuck 巡检等。
-- ============================================================

-- ---------- 1. 存量数据迁移（秒 -> 毫秒，幂等） ----------
UPDATE `device_record` SET `device_createtime` = `device_createtime` * 1000
WHERE `device_createtime` < 10000000000;

UPDATE `device_record` SET `device_updatetime` = `device_updatetime` * 1000
WHERE `device_updatetime` IS NOT NULL AND `device_updatetime` < 10000000000;

UPDATE `device_command_log` SET `command_createtime` = `command_createtime` * 1000
WHERE `command_createtime` < 10000000000;

UPDATE `device_command_log` SET `command_updatetime` = `command_updatetime` * 1000
WHERE `command_updatetime` IS NOT NULL AND `command_updatetime` < 10000000000;

UPDATE `device_alarm` SET `alarm_createtime` = `alarm_createtime` * 1000
WHERE `alarm_createtime` < 10000000000;

UPDATE `device_alarm` SET `alarm_handled_time` = `alarm_handled_time` * 1000
WHERE `alarm_handled_time` IS NOT NULL AND `alarm_handled_time` < 10000000000;

-- ---------- 2. 列注释同步（毫秒语义） ----------
ALTER TABLE `device_record` MODIFY COLUMN `device_createtime` bigint NOT NULL COMMENT '登记时间戳(毫秒, D-H)';
ALTER TABLE `device_record` MODIFY COLUMN `device_updatetime` bigint DEFAULT NULL COMMENT '更新时间戳(毫秒, D-H)';
ALTER TABLE `device_command_log` MODIFY COLUMN `command_createtime` bigint NOT NULL COMMENT '下发时间戳(毫秒, D-H)';
ALTER TABLE `device_command_log` MODIFY COLUMN `command_updatetime` bigint DEFAULT NULL COMMENT '更新时间戳(毫秒, D-H)';
ALTER TABLE `device_alarm` MODIFY COLUMN `alarm_createtime` bigint NOT NULL COMMENT '告警时间戳(毫秒, D-H)';
ALTER TABLE `device_alarm` MODIFY COLUMN `alarm_handled_time` bigint DEFAULT NULL COMMENT '处理时间戳(毫秒, D-H)';
