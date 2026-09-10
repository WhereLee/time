-- ============================================================
-- 07-barrier-stage4.sql（升降杆样例：批次4 告警增强与任务看护）
-- 执行时机：03/05/06 之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) 注册 barrierTaskWatchdogTask（job_id=202，每分钟任务看护）
--   2) device_alarm.alarm_type 注释更新（7-批量离线 8-任务停摆 9-对账持续异常）
--
-- 设计说明：
--   - 看护对象=schedule_job 中启用的 barrier 家族任务（动态发现自动纳入新增任务；排除自身）；
--     判据=schedule_job_log 中 log_state=0 的最大时间错过 N 次 cron 触发
--     （reason.barrier.job-stall-missed-triggers，默认 2）——不改动通用 Quartz 框架；
--   - 触发点 '10 * * * * ?' 与 200（0 秒）/201（0/30 秒）错开，避免同刻扎堆；
--   - 停摆告警 JOB_STALLED(8) 落 device_alarm，device_no='__job:{jobId}__'（每任务独立去重键）；
--   - 恢复（下一轮检查未错过）自动关闭告警（closeUnhandledAlarm）。
-- ============================================================

-- ---------- 1. 任务看护注册（固定 job_id 202，先删后插幂等） ----------
DELETE FROM `schedule_job` WHERE `job_id` = 202;
INSERT INTO `schedule_job`
  (`job_id`, `job_bean`, `job_name`, `job_params`, `job_cron`,
   `job_state`, `job_comment`, `job_createtime`, `job_updatetime`, `job_status`)
VALUES
  (202, 'barrierTaskWatchdogTask', '升降杆任务看护', NULL, '10 * * * * ?',
   0, '每分钟：看护 barrier 家族任务（排除自身）——最后成功时间错过 2 次 cron 触发->停摆告警；恢复自动关',
   unix_timestamp(now()), unix_timestamp(now()), 0);

-- ---------- 2. 告警类型注释更新（1-9，与 AlarmType 枚举同步） ----------
ALTER TABLE `device_alarm` MODIFY COLUMN `alarm_type` tinyint NOT NULL
  COMMENT '类型：1-指令重试超限 2-设备离线 3-状态对账不一致 4-设备故障上报 5-自动校正连续失败 6-动作卡死未到位 7-批量离线 8-任务停摆 9-对账持续异常';
