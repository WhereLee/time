-- ============================================================
-- 07-充电联调预置.sql
-- 执行时机：02-parking + 04-charging 之后（本地联调/云服务器演示数据；CI 容器不挂载）
-- 内容：充电车位 B-001~003（停车域车位主数据）+ 充电桩 PILE-001~003 绑 B 位（sim 设备编号与桩编号一致）
-- 幂等性：INSERT IGNORE——space_no/pile_no 唯一冲突即跳过，可重复执行
-- ============================================================

INSERT IGNORE INTO `park_space`
  (`space_no`, `space_area`, `space_state`, `space_creator`, `space_createtime`, `space_updatetime`)
VALUES
  ('B-001', 'B区-充电位', 0, 2, unix_timestamp(now()), unix_timestamp(now())),
  ('B-002', 'B区-充电位', 0, 2, unix_timestamp(now()), unix_timestamp(now())),
  ('B-003', 'B区-充电位', 0, 2, unix_timestamp(now()), unix_timestamp(now()));

INSERT IGNORE INTO `charging_pile`
  (`pile_no`, `space_id`, `pile_state`, `pile_creator`, `pile_createtime`, `pile_updatetime`)
SELECT 'PILE-001', space_id, 0, 2, unix_timestamp(now()), unix_timestamp(now())
FROM `park_space` WHERE space_no = 'B-001';

INSERT IGNORE INTO `charging_pile`
  (`pile_no`, `space_id`, `pile_state`, `pile_creator`, `pile_createtime`, `pile_updatetime`)
SELECT 'PILE-002', space_id, 0, 2, unix_timestamp(now()), unix_timestamp(now())
FROM `park_space` WHERE space_no = 'B-002';

INSERT IGNORE INTO `charging_pile`
  (`pile_no`, `space_id`, `pile_state`, `pile_creator`, `pile_createtime`, `pile_updatetime`)
SELECT 'PILE-003', space_id, 0, 2, unix_timestamp(now()), unix_timestamp(now())
FROM `park_space` WHERE space_no = 'B-003';
