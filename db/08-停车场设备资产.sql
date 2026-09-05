-- ============================================================
-- 08-停车场设备资产.sql（A 块设备域：300 车位商超停车场资产重设）
-- 执行时机：02-parking + 04-charging 之后（CI 容器挂载验证幂等；本地/云上演示数据源）
-- 内容：
--   1) 新建 device_online 设备在线台账（闸机/位检/充电桩统一在线语义，业务台账仍在 parking/charging 各自域）
--   2) 资产重设（删旧建新，幂等）：park_space 300 车位（A 普通 140 + B 普通 130 + C 充电 30）
--      + charging_pile 30 桩（PILE-001~030 绑 C-001~030）+ device_online 336 行（闸机 6 + 位检 300 + 桩 30）
-- 幂等性：本脚本为"演示资产重建"语义——重复执行 = 先清预置区段再全量重建；
--   删除范围按编号段（A-/B-/C- 前缀 + PILE-%），不触碰 IT 前缀测试数据与业务流水
-- ============================================================

-- ---------- 1. 设备在线台账（统一在线语义，A 块新表） ----------
DROP TABLE IF EXISTS `device_online`;
CREATE TABLE `device_online` (
  `device_id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '设备id',
  `device_no`        varchar(32)  NOT NULL COMMENT '设备编号（全局唯一，心跳上报/指令寻址；充电桩与桩编号一致）',
  `device_type`      tinyint      NOT NULL COMMENT '类型：0-入口闸机 1-出口闸机 2-位检 3-充电桩',
  `bind_target`      varchar(64)  NULL COMMENT '绑定对象：出入口编码(E/S/W)/车位号/桩号（不直连业务表，纯标识）',
  `device_state`     tinyint      NOT NULL DEFAULT 0 COMMENT '在线态：0-离线 1-在线',
  `last_heartbeat`   bigint       NULL COMMENT '最后心跳时间（秒）',
  `device_remark`    varchar(255) NULL COMMENT '备注（位置/车道说明）',
  `device_creator`   bigint       NULL COMMENT '创建人（sys_user.user_id）',
  `device_createtime` bigint      NOT NULL COMMENT '创建时间（秒）',
  `device_updatetime` bigint      NULL COMMENT '更新时间（秒）',
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `u_device_no` (`device_no`) COMMENT '设备编号全局唯一',
  KEY `idx_type_state` (`device_type`, `device_state`) COMMENT '按类型+在线态查询（管理端设备面）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备在线台账：闸机/位检/充电桩统一在线语义；业务台账(park_space/charging_pile)在各业务域，本表只持绑定标识';

-- ---------- 2. 资产重设：清预置区段（重复执行安全；IT 前缀测试数据不匹配删除条件） ----------
DELETE FROM `charging_pile` WHERE pile_no LIKE 'PILE-%';
DELETE FROM `park_space` WHERE space_no LIKE 'A-___' OR space_no LIKE 'B-___' OR space_no LIKE 'C-___';
DELETE FROM `device_online`;

-- ---------- 3. 车位 300：A 普通 140 / B 普通 130 / C 充电 30 ----------
INSERT INTO `park_space`
  (`space_no`, `space_area`, `space_state`, `space_creator`, `space_createtime`, `space_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 140)
SELECT CONCAT('A-', LPAD(n, 3, '0')), 'A区-普通车位', 0, 2, unix_timestamp(now()), unix_timestamp(now()) FROM seq;

INSERT INTO `park_space`
  (`space_no`, `space_area`, `space_state`, `space_creator`, `space_createtime`, `space_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 130)
SELECT CONCAT('B-', LPAD(n, 3, '0')), 'B区-普通车位', 0, 2, unix_timestamp(now()), unix_timestamp(now()) FROM seq;

INSERT INTO `park_space`
  (`space_no`, `space_area`, `space_state`, `space_creator`, `space_createtime`, `space_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT CONCAT('C-', LPAD(n, 3, '0')), 'C区-充电车位', 0, 2, unix_timestamp(now()), unix_timestamp(now()) FROM seq;

-- ---------- 4. 充电桩 30：PILE-001~030 绑 C-001~030（充电专区集中，1 车位 1 桩） ----------
INSERT INTO `charging_pile`
  (`pile_no`, `space_id`, `pile_state`, `pile_creator`, `pile_createtime`, `pile_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT CONCAT('PILE-', LPAD(n, 3, '0')),
       (SELECT space_id FROM `park_space` WHERE space_no = CONCAT('C-', LPAD(n, 3, '0'))),
       0, 2, unix_timestamp(now()), unix_timestamp(now())
FROM seq;

-- ---------- 5. 设备台账 336 行 ----------
-- 5.1 出入口闸机 6 台：东/南/西 × 入口/出口（出入口编码 E/S/W）
INSERT INTO `device_online`
  (`device_no`, `device_type`, `bind_target`, `device_state`, `device_remark`, `device_creator`, `device_createtime`, `device_updatetime`)
VALUES
  ('GATE-E-IN',  0, 'E', 0, '东出入口-入口闸机', 2, unix_timestamp(now()), unix_timestamp(now())),
  ('GATE-E-OUT', 1, 'E', 0, '东出入口-出口闸机', 2, unix_timestamp(now()), unix_timestamp(now())),
  ('GATE-S-IN',  0, 'S', 0, '南出入口-入口闸机', 2, unix_timestamp(now()), unix_timestamp(now())),
  ('GATE-S-OUT', 1, 'S', 0, '南出入口-出口闸机', 2, unix_timestamp(now()), unix_timestamp(now())),
  ('GATE-W-IN',  0, 'W', 0, '西出入口-入口闸机', 2, unix_timestamp(now()), unix_timestamp(now())),
  ('GATE-W-OUT', 1, 'W', 0, '西出入口-出口闸机', 2, unix_timestamp(now()), unix_timestamp(now()));

-- 5.2 位检 300 台：每车位 1 台（device_no 与车位号同构 SENSOR-{车位号}，bind_target=车位号）
INSERT INTO `device_online`
  (`device_no`, `device_type`, `bind_target`, `device_state`, `device_creator`, `device_createtime`, `device_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 140)
SELECT CONCAT('SENSOR-A-', LPAD(n, 3, '0')), 2, CONCAT('A-', LPAD(n, 3, '0')), 0, 2, unix_timestamp(now()), unix_timestamp(now()) FROM seq;

INSERT INTO `device_online`
  (`device_no`, `device_type`, `bind_target`, `device_state`, `device_creator`, `device_createtime`, `device_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 130)
SELECT CONCAT('SENSOR-B-', LPAD(n, 3, '0')), 2, CONCAT('B-', LPAD(n, 3, '0')), 0, 2, unix_timestamp(now()), unix_timestamp(now()) FROM seq;

INSERT INTO `device_online`
  (`device_no`, `device_type`, `bind_target`, `device_state`, `device_creator`, `device_createtime`, `device_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT CONCAT('SENSOR-C-', LPAD(n, 3, '0')), 2, CONCAT('C-', LPAD(n, 3, '0')), 0, 2, unix_timestamp(now()), unix_timestamp(now()) FROM seq;

-- 5.3 充电桩 30 台：设备编号与桩编号一致（sim 寻址沿用 M1 语义 deviceNo=PILE-xxx）
INSERT INTO `device_online`
  (`device_no`, `device_type`, `bind_target`, `device_state`, `device_creator`, `device_createtime`, `device_updatetime`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT CONCAT('PILE-', LPAD(n, 3, '0')), 3, CONCAT('PILE-', LPAD(n, 3, '0')), 0, 2, unix_timestamp(now()), unix_timestamp(now()) FROM seq;

-- ---------- 6. 计数自检（期望：车位 300 / 桩 30 / 设备 306） ----------
SELECT 'park_space', COUNT(*) FROM `park_space`;
SELECT 'charging_pile', COUNT(*) FROM `charging_pile`;
SELECT 'device_online', COUNT(*) FROM `device_online`;
