-- ============================================================
-- 09-设备在线巡检job注册.sql（A 块设备在线体系）
-- 执行时机：01 建库之后（schedule_job 已存在，应用启动时 ScheduleJobServiceImpl @PostConstruct 全量注册）
-- 幂等性：INSERT IGNORE（u_jobbean(job_bean, job_status=0) 唯一冲突即跳过），可重复执行
-- ============================================================

INSERT IGNORE INTO `schedule_job`
  (`job_bean`, `job_name`, `job_params`, `job_cron`, `job_state`, `job_comment`,
   `job_createtime`, `job_updatetime`, `job_status`)
VALUES
  ('deviceOnlineScanTask', '设备在线巡检', '', '0/15 * * * * ?', 0,
   '每 15 秒：心跳超时（30s）的在线设备批量置离线（在线台账最终裁决，心跳周期 10s 容忍 2 个周期）',
   unix_timestamp(now()), unix_timestamp(now()), 0);
