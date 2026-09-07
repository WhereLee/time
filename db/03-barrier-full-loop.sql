-- ============================================================
-- 03-barrier-full-loop.sql（升降杆样例：完全闭环 块4-5）
-- 执行时机：02 台账之后（本地/CI 均幂等可重复执行）
-- 内容：
--   1) device_command_log 指令流水表（每次下发的"账"：seq幂等/触发源/到位状态/重试次数）
--   2) device_alarm 告警表（超时/离线/对账不一致/设备故障的显式留痕）
--   3) schedule_job 注册两个 Quartz 任务（自动规则对账 + 超时离线监控）
--   4) 菜单按钮权限（device:alarm:list / device:commandlog:list）+ 管理员授权
--
-- 设计说明（与对象设计/五机制对应）：
--   - 指令流水 = "重试幂等"的数据基础：seq 设备内唯一，重发同 seq 设备侧去重；
--     平台按"待到位超时"扫描重试，超限转告警（失败显式化，不悄悄重试）。
--   - 告警表 = "故障态+告警"机制的落点：平台发现异常只能喊（告警），
--     不能替设备写状态（台账状态仍只信设备事件——铁律不破）。
--   - Quartz misfire 策略为 DoNothing（框架 ScheduleUtils 既定）：
--     错过的触发不补跑，由每分钟周期对账自然吸收（应然 vs 实然校正）。
-- ============================================================

-- ---------- 1. 指令流水表 ----------
DROP TABLE IF EXISTS `device_command_log`;
CREATE TABLE `device_command_log` (
  `command_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_no` varchar(32) NOT NULL COMMENT '设备编号',
  `command_action` varchar(16) NOT NULL COMMENT '指令动作：OPEN-升起 CLOSE-降下',
  `command_seq` bigint NOT NULL COMMENT '指令序号（Redis INCR 生成，设备内单调递增；重发同 seq = 幂等去重键）',
  `trigger_type` tinyint NOT NULL DEFAULT '1' COMMENT '触发源：1-管理端手动 2-自动规则 3-超时重试',
  `command_status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-已下发待到位 1-已到位 2-下发失败 3-重试超限转故障 4-执行失败(设备故障)',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数（超限阈值见 reason.barrier.max-retry）',
  `command_createtime` bigint NOT NULL COMMENT '下发时间戳(秒)',
  `command_updatetime` bigint DEFAULT NULL COMMENT '更新时间戳(秒)',
  PRIMARY KEY (`command_id`),
  UNIQUE KEY `u_device_seq` (`device_no`,`command_seq`) COMMENT '设备内 seq 唯一（幂等硬保证）',
  KEY `idx_status_time` (`command_status`,`command_createtime`) COMMENT '超时扫描：待到位+时间'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备指令流水（指令闭环的账本；升降杆样例）';

-- ---------- 2. 告警表 ----------
DROP TABLE IF EXISTS `device_alarm`;
CREATE TABLE `device_alarm` (
  `alarm_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_no` varchar(32) NOT NULL COMMENT '设备编号',
  `alarm_type` tinyint NOT NULL COMMENT '类型：1-指令重试超限 2-设备离线 3-状态对账不一致 4-设备故障上报',
  `alarm_content` varchar(512) DEFAULT NULL COMMENT '告警内容（人可读）',
  `alarm_handled` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态：0-未处理 1-已处理',
  `alarm_createtime` bigint NOT NULL COMMENT '告警时间戳(秒)',
  PRIMARY KEY (`alarm_id`),
  KEY `idx_device_handled` (`device_no`,`alarm_handled`) COMMENT '按设备查未处理告警',
  KEY `idx_type_time` (`alarm_type`,`alarm_createtime`) COMMENT '按类型+时间检索'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备告警（异常显式化留痕；升降杆样例）';

-- ---------- 3. Quartz 任务注册（固定 job_id 200/201，幂等：先删后插） ----------
-- barrierAutoTask：每分钟"应然 vs 实然"对账（自动升降本体；错过由下轮吸收）
-- barrierMonitorTask：每30秒指令超时对账 + 重试/超限告警 + 离线判定告警
DELETE FROM `schedule_job` WHERE `job_id` IN (200, 201);
INSERT INTO `schedule_job`
  (`job_id`, `job_bean`, `job_name`, `job_params`, `job_cron`,
   `job_state`, `job_comment`, `job_createtime`, `job_updatetime`, `job_status`)
VALUES
  (200, 'barrierAutoTask', '升降杆自动规则对账', NULL, '0 * * * * ?',
   0, '每分钟：问时间规则应然状态，与台账实然比对，不一致且无手动保持期则下发校正指令',
   unix_timestamp(now()), unix_timestamp(now()), 0),
  (201, 'barrierMonitorTask', '升降杆超时与离线监控', NULL, '0/30 * * * * ?',
   0, '每30秒：待到位指令超时重试(同seq幂等)/超限转告警；心跳过期设备离线告警(去重)',
   unix_timestamp(now()), unix_timestamp(now()), 0);

-- ---------- 4. 菜单按钮权限（固定 id 205/206，挂 200 设备台账菜单下） ----------
DELETE FROM `sys_menu` WHERE `menu_id` IN (205, 206);
INSERT INTO `sys_menu`
  (`menu_id`, `menu_origin`, `menu_type`, `menu_name`, `menu_perms`,
   `menu_icon`, `menu_pic`, `menu_def_pic`, `menu_url`, `menu_page`,
   `menu_fid`, `menu_fids`, `menu_ordernum`,
   `menu_createtime`, `menu_updatetime`, `menu_status`)
VALUES
  (205, 1, 2, '告警查询', 'device:alarm:list', NULL, NULL, NULL, NULL, NULL,
   200, '0,200', 0, unix_timestamp(now()), unix_timestamp(now()), 0),
  (206, 1, 2, '指令流水查询', 'device:commandlog:list', NULL, NULL, NULL, NULL, NULL,
   200, '0,200', 0, unix_timestamp(now()), unix_timestamp(now()), 0);

-- ---------- 5. 系统管理员(role_id=2) 授权（幂等） ----------
DELETE FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` IN (205, 206);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_id` IN (205, 206);
