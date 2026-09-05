-- ============================================================
-- 04-charging.sql：充电域（charging）表结构 + 停车订单减免列 + 充电菜单注册
-- 执行时机：01 基线 + 02-parking + 03 授权之后（依赖 sys_menu 200-208 / park_space / fee_rule）
-- 幂等性说明：
--   · park_order 扩展列：information_schema 判断 + PREPARE 动态 ALTER（MySQL8 无 ADD COLUMN IF NOT EXISTS）
--   · 新表 DROP+CREATE（与 02 同风格，面向新库导入；服务器一次性执行）
--   · 费率预置/菜单注册：INSERT IGNORE（主键冲突即跳过，可重复执行）
-- 风格：时间 bigint 秒级 epoch；utf8mb4_general_ci；金额/电量整数存储；无物理删除
-- ============================================================

-- ---------- 停车订单减免快照列（幂等 ALTER，勿手工重复执行） ----------
SET @has_discount := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'park_order' AND COLUMN_NAME = 'discount_fen');
SET @ddl_discount := IF(@has_discount = 0,
    'ALTER TABLE `park_order`
     ADD COLUMN `discount_fen` bigint NOT NULL DEFAULT 0 COMMENT ''减免金额（分，无减免为 0；amount_fen 保持减免前应收原义）'' AFTER `amount_fen`,
     ADD COLUMN `benefit_no` varchar(32) NULL COMMENT ''核销权益码快照（无减免为空）'' AFTER `discount_fen`',
    'SELECT 1');
PREPARE stmt_discount FROM @ddl_discount;
EXECUTE stmt_discount;
DEALLOCATE PREPARE stmt_discount;

-- ---------- 充电费率（计费各域自治：不扩 fee_rule；单价 fen/kWh 整数） ----------
DROP TABLE IF EXISTS `charge_fee_rule`;
CREATE TABLE `charge_fee_rule` (
  `rule_id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '规则id',
  `rule_name`         varchar(64) NOT NULL COMMENT '规则名称',
  `elec_price_fen`    int         NOT NULL COMMENT '电费单价（分/千瓦时，整数）',
  `service_price_fen` int         NOT NULL COMMENT '服务费单价（分/千瓦时，整数）',
  `rule_state`        tinyint     NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用（M1 仅默认启用一条）',
  `rule_remark`       varchar(255) NULL COMMENT '备注',
  `rule_createtime`   bigint      NOT NULL COMMENT '创建时间（秒）',
  PRIMARY KEY (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='充电费率（电费+服务费两段，M1 固定价单规则）';

INSERT IGNORE INTO `charge_fee_rule`
  (`rule_id`, `rule_name`, `elec_price_fen`, `service_price_fen`, `rule_state`, `rule_remark`, `rule_createtime`)
VALUES (1, '标准充电费率', 80, 40, 1, '电费 0.8 元/度 + 服务费 0.4 元/度（M1 唯一规则，峰谷 M2）', unix_timestamp(now()));

-- ---------- 充电桩（绑车位 1:1，空间主数据仍在 parking.park_space） ----------
DROP TABLE IF EXISTS `charging_pile`;
CREATE TABLE `charging_pile` (
  `pile_id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '桩id',
  `pile_no`         varchar(32) NOT NULL COMMENT '桩编号（唯一）',
  `space_id`        bigint      NOT NULL COMMENT '绑定车位id（park_space.space_id，1:1 不建 DB 外键）',
  `pile_state`      tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-空闲 1-充电中 2-停用',
  `pile_creator`    bigint      NOT NULL COMMENT '创建人（sys_user.user_id）',
  `pile_createtime` bigint      NOT NULL COMMENT '创建时间（秒）',
  `pile_updatetime` bigint      NULL COMMENT '更新时间（秒）',
  PRIMARY KEY (`pile_id`),
  UNIQUE KEY `u_pile_no` (`pile_no`),
  UNIQUE KEY `u_pile_space` (`space_id`) COMMENT '一车位一桩（充电车位专用位语义）',
  KEY `idx_pile_state` (`pile_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='充电桩台账（充电车位 = 带桩车位，空间模型共享）';

-- ---------- 充电会话（锚定停车会话：充电必须发生在停车中） ----------
DROP TABLE IF EXISTS `charge_session`;
CREATE TABLE `charge_session` (
  `session_id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '会话id',
  `pile_id`            bigint      NOT NULL COMMENT '桩id',
  `pile_no`            varchar(32) NOT NULL COMMENT '桩编号快照',
  `space_id`           bigint      NOT NULL COMMENT '车位id',
  `space_no`           varchar(32) NOT NULL COMMENT '车位编号快照',
  `plate_no`           varchar(16) NOT NULL COMMENT '车牌号（与锚定停车会话车牌一致校验）',
  `anchor_session_id`  bigint      NOT NULL COMMENT '锚定停车会话id（park_session.session_id，充电时该车位必须在进行中停车）',
  `session_start_time` bigint      NOT NULL COMMENT '开始充电时间（秒）',
  `session_end_time`   bigint      NULL COMMENT '结束时间（秒，未结束为空）',
  `energy_wh`          bigint      NULL COMMENT '总电量（瓦时，结束/超时上报后落值）',
  `session_state`      tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-充电中 1-已结束 2-已取消 3-超时结束',
  `cancel_reason`      varchar(255) NULL COMMENT '取消原因（取消/超时巡检原因）',
  `session_createtime` bigint      NOT NULL COMMENT '创建时间（秒）',
  `session_updatetime` bigint      NULL COMMENT '更新时间（秒）',
  PRIMARY KEY (`session_id`),
  KEY `idx_space_state` (`space_id`, `session_state`) COMMENT '并发兜底查询：车位无充电中会话',
  KEY `idx_anchor` (`anchor_session_id`) COMMENT '权益锚定链查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='充电会话（充电中→已结束/已取消/超时结束，终态不可逆）';

-- ---------- 充电订单（结算时点快照，生成后不可变；金额统一分存储） ----------
DROP TABLE IF EXISTS `charge_order`;
CREATE TABLE `charge_order` (
  `order_id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '订单id',
  `session_id`         bigint      NOT NULL COMMENT '会话id（charge_session.session_id，唯一）',
  `pile_no`            varchar(32) NOT NULL COMMENT '桩编号快照',
  `space_no`           varchar(32) NOT NULL COMMENT '车位编号快照',
  `plate_no`           varchar(16) NOT NULL COMMENT '车牌号快照',
  `order_start_time`   bigint      NOT NULL COMMENT '开始充电时间快照（秒）',
  `order_end_time`     bigint      NOT NULL COMMENT '结束时间快照（秒）',
  `energy_wh`          bigint      NOT NULL COMMENT '电量快照（瓦时）',
  `elec_price_fen`     int         NOT NULL COMMENT '电费单价快照（分/千瓦时）',
  `service_price_fen`  int         NOT NULL COMMENT '服务费单价快照（分/千瓦时）',
  `elec_amount_fen`    bigint      NOT NULL COMMENT '电费金额（分，各自四舍五入）',
  `service_amount_fen` bigint      NOT NULL COMMENT '服务费金额（分，各自四舍五入）',
  `amount_fen`         bigint      NOT NULL COMMENT '总金额（分 = 电费金额 + 服务费金额，快照恒等）',
  `order_state`        tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-已生成（M1 终态，无支付语义）',
  `order_createtime`   bigint      NOT NULL COMMENT '创建时间（秒）',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `u_order_session` (`session_id`) COMMENT '一会话一订单（防并发重复结算）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='充电订单（电量两段计费快照，生成后不可变）';

-- ---------- 免停权益（跨方凭证：charging 签发，parking 凭码核销） ----------
DROP TABLE IF EXISTS `benefit_record`;
CREATE TABLE `benefit_record` (
  `benefit_id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '权益id',
  `benefit_no`         varchar(32) NOT NULL COMMENT '权益码（凭证：BN+时间戳+随机，核销判官键）',
  `source_order_id`    bigint      NOT NULL COMMENT '来源充电订单id（charge_order.order_id）',
  `plate_no`           varchar(16) NOT NULL COMMENT '车牌号',
  `anchor_session_id`  bigint      NOT NULL COMMENT '锚定停车会话id（继承自充电会话，核销须同会话）',
  `free_seconds`       int         NOT NULL COMMENT '免停时长（秒，签发时按规则固化）',
  `expire_time`        bigint      NOT NULL COMMENT '到期时间（秒，过期待调度 job 置过期）',
  `benefit_state`      tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-可用 1-已核销 2-已过期',
  `redeem_session_id`  bigint      NULL COMMENT '核销停车会话id（判官回写）',
  `redeem_order_id`    bigint      NULL COMMENT '核销停车订单id（快照关联）',
  `benefit_createtime` bigint      NOT NULL COMMENT '签发时间（秒）',
  `redeem_time`        bigint      NULL COMMENT '核销时间（秒）',
  PRIMARY KEY (`benefit_id`),
  UNIQUE KEY `u_benefit_no` (`benefit_no`),
  KEY `idx_anchor` (`anchor_session_id`),
  KEY `idx_state_expire` (`benefit_state`, `expire_time`) COMMENT '过期 job 扫描索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='免停权益（跨方凭证，可用→已核销/已过期，一次有效）';

-- ---------- 充电菜单注册（固定 menu_id 300-310 幂等；对齐 02 停车菜单 16 列风格） ----------
INSERT IGNORE INTO `sys_menu`
  (`menu_id`, `menu_origin`, `menu_type`, `menu_name`, `menu_perms`,
   `menu_icon`, `menu_pic`, `menu_def_pic`, `menu_url`, `menu_page`,
   `menu_fid`, `menu_fids`, `menu_ordernum`,
   `menu_createtime`, `menu_updatetime`, `menu_status`)
VALUES
  (300, 1, 0, '充电管理', NULL, NULL, NULL, NULL, NULL, '/charging',
   0, '0', 2, unix_timestamp(now()), unix_timestamp(now()), 0),
  (301, 1, 1, '桩位管理', NULL, NULL, NULL, NULL, NULL, '/charging/pile',
   300, '0,300', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (302, 1, 2, '桩位查询', 'charge:pile:list,charge:pile:info', NULL, NULL, NULL, NULL, NULL,
   301, '0,300,301', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (303, 1, 2, '桩位新增', 'charge:pile:save', NULL, NULL, NULL, NULL, NULL,
   301, '0,300,301', 2, unix_timestamp(now()), unix_timestamp(now()), 0),
  (304, 1, 2, '桩位编辑', 'charge:pile:update', NULL, NULL, NULL, NULL, NULL,
   301, '0,300,301', 3, unix_timestamp(now()), unix_timestamp(now()), 0),
  (305, 1, 1, '充电会话', NULL, NULL, NULL, NULL, NULL, '/charging/session',
   300, '0,300', 2, unix_timestamp(now()), unix_timestamp(now()), 0),
  (306, 1, 2, '会话查询', 'charge:session:list', NULL, NULL, NULL, NULL, NULL,
   305, '0,300,305', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (307, 1, 1, '充电订单', NULL, NULL, NULL, NULL, NULL, '/charging/order',
   300, '0,300', 3, unix_timestamp(now()), unix_timestamp(now()), 0),
  (308, 1, 2, '订单查询', 'charge:order:list', NULL, NULL, NULL, NULL, NULL,
   307, '0,300,307', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (309, 1, 1, '权益记录', NULL, NULL, NULL, NULL, NULL, '/charging/benefit',
   300, '0,300', 4, unix_timestamp(now()), unix_timestamp(now()), 0),
  (310, 1, 2, '权益查询', 'charge:benefit:list', NULL, NULL, NULL, NULL, NULL,
   309, '0,300,309', 1, unix_timestamp(now()), unix_timestamp(now()), 0);
