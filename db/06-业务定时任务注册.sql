-- ============================================================
-- 06-业务定时任务注册.sql
-- 执行时机：01 基线之后（依赖 schedule_job 表）；业务 task bean 随应用部署后重启生效（ScheduleJobServiceImpl @PostConstruct 全量注册）
-- 幂等性：INSERT IGNORE——u_jobbean(job_bean, job_status=0) 唯一冲突即跳过，可重复执行
-- 治理口径：job_state=0 正常；cron 修改走管理端定时任务页（ScheduleUtils.update 更新触发器）
-- ============================================================

INSERT IGNORE INTO `schedule_job`
  (`job_bean`, `job_name`, `job_params`, `job_cron`, `job_state`, `job_comment`,
   `job_createtime`, `job_updatetime`, `job_status`)
VALUES
  ('benefitExpireTask', '免停权益过期作废', '', '0 * * * * ?', 0,
   '每分钟：可用且已到期权益置已过期（条件更新幂等；M1 调度转正 1/4）',
   unix_timestamp(now()), unix_timestamp(now()), 0),
  ('chargeSessionTimeoutTask', '充电会话超时巡检', '', '0 0/5 * * * ?', 0,
   '每 5 分钟：充电中超过 2h 悬挂会话强制超时结束（0 电 0 元订单 + 不发权益 + 桩释放）',
   unix_timestamp(now()), unix_timestamp(now()), 0),
  ('parkLongOccupancyTask', '长期占用巡检', '', '0 0 * * * ?', 0,
   '每小时：停车进行中超过 3 天告警不动账（运营动作人工决策）',
   unix_timestamp(now()), unix_timestamp(now()), 0),
  ('reconcileTask', '跨方对账', '', '0 30 * * * ?', 0,
   '每小时半点：签发完整性/核销完整性/快照一致性三组对账，差异告警',
   unix_timestamp(now()), unix_timestamp(now()), 0);
