-- ============================================================
-- 10-设备管理.sql（A 块设备域管理端：闸机人工操作留痕表 + 设备管理菜单 + 管理员授权）
-- 执行时机：02-parking + 08 资产之后（device_online 已建；sys_menu 结构已存在）
-- 内容：
--   1) gate_manual_op 闸机人工操作留痕表（手动抬杆等：操作人/原因/车牌必录，防逃费审计依据）
--   2) 设备管理菜单 400-405（组/台账页/台账查询/手动抬杆/抬杆记录页/记录查询）
--   3) 系统管理员（role_id=2）授权
-- 幂等性：新表 DROP+CREATE；菜单 INSERT IGNORE（menu_id 固定）；授权 INSERT...WHERE NOT EXISTS
-- ============================================================

-- ---------- 1. 闸机人工操作留痕表 ----------
DROP TABLE IF EXISTS `gate_manual_op`;
CREATE TABLE `gate_manual_op` (
  `op_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '操作id',
  `device_no`      varchar(32)  NOT NULL COMMENT '目标闸机设备号（如 GATE-E-OUT）',
  `gate_code`      varchar(8)   NULL COMMENT '出入口编码（E/S/W，冗余自设备号便于检索）',
  `op_type`        tinyint      NOT NULL DEFAULT 1 COMMENT '操作类型：1-手动抬杆',
  `plate_no`       varchar(16)  NULL COMMENT '车牌号（人工录入；空=未识别/无牌）',
  `op_reason`      varchar(256) NULL COMMENT '操作原因（必录：设备故障/特殊放行/收费争议）',
  `op_result`      tinyint      NOT NULL DEFAULT 0 COMMENT '指令结果：0-成功 1-设备不可达',
  `op_remark`      varchar(256) NULL COMMENT '结果说明（失败原因等）',
  `operator_id`    bigint       NOT NULL COMMENT '操作人（sys_user.user_id）',
  `operator_name`  varchar(64)  NOT NULL COMMENT '操作人账号冗余（列表展示免 join）',
  `op_createtime`  bigint       NOT NULL COMMENT '操作时间（秒）',
  PRIMARY KEY (`op_id`),
  KEY `idx_device_time` (`device_no`, `op_createtime`) COMMENT '按设备查操作史',
  KEY `idx_plate_time` (`plate_no`, `op_createtime`) COMMENT '按车牌查放行史（逃费追查）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='闸机人工操作留痕：手动抬杆等异常放行审计（操作人/原因/车牌必录，防止人工放行成为逃费通道）';

-- ---------- 2. 设备管理菜单（400 组；对齐 02/04 停车/充电菜单 16 列风格） ----------
INSERT IGNORE INTO `sys_menu`
  (`menu_id`, `menu_origin`, `menu_type`, `menu_name`, `menu_perms`,
   `menu_icon`, `menu_pic`, `menu_def_pic`, `menu_url`, `menu_page`,
   `menu_fid`, `menu_fids`, `menu_ordernum`,
   `menu_createtime`, `menu_updatetime`, `menu_status`)
VALUES
  (400, 1, 0, '设备管理', NULL, NULL, NULL, NULL, NULL, '/parking/device',
   0, '0', 5, unix_timestamp(now()), unix_timestamp(now()), 0),
  (401, 1, 1, '设备台账', NULL, NULL, NULL, NULL, NULL, '/parking/device',
   400, '0,400', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (402, 1, 2, '台账查询', 'device:online:list', NULL, NULL, NULL, NULL, NULL,
   401, '0,400,401', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (403, 1, 2, '手动抬杆', 'device:gate:lift', NULL, NULL, NULL, NULL, NULL,
   401, '0,400,401', 2, unix_timestamp(now()), unix_timestamp(now()), 0),
  (404, 1, 1, '抬杆记录', NULL, NULL, NULL, NULL, NULL, '/parking/device/gatelog',
   400, '0,400', 2, unix_timestamp(now()), unix_timestamp(now()), 0),
  (405, 1, 2, '记录查询', 'device:gatelog:list', NULL, NULL, NULL, NULL, NULL,
   404, '0,400,404', 1, unix_timestamp(now()), unix_timestamp(now()), 0);

-- ---------- 3. 系统管理员授权（role_id=2，对齐 03/05 授权模式） ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, menu_id FROM sys_menu
WHERE menu_id BETWEEN 400 AND 405
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = sys_menu.menu_id
  );
