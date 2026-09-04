-- ============================================================
-- 02-parking.sql  停车域（parking 限界上下文）
-- 执行方式：本地 mysql source；CI 挂载为 docker-entrypoint-initdb.d/02-parking.sql
--   （01-reason-faster.sql 为壳子基线冻结，本文件按文件名序在其后执行）
-- 幂等性：parking 域表 DROP TABLE IF EXISTS 重建；sys_menu 注册 INSERT IGNORE（菜单表属基线库，不重建）
-- 约定：Navicat 全量导出永远另存（如 db/full-backup-日期.sql），不得覆盖 01 基线文件
-- 编码：本文件 UTF-8；执行客户端需 set names utf8mb4
-- ============================================================

-- ----------------------------
-- 车位台账（设备控制型智慧停车位；无物理删除，删除=禁用）
-- ----------------------------
DROP TABLE IF EXISTS `park_space`;
CREATE TABLE `park_space` (
  `space_id`        bigint      NOT NULL AUTO_INCREMENT COMMENT '车位id',
  `space_no`        varchar(32) NOT NULL COMMENT '车位编号（如 A-001，全局唯一）',
  `space_area`      varchar(64) NULL COMMENT '区域/位置描述（如：A区-东侧）',
  `space_state`     tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-空闲 1-占用 2-禁用',
  `space_creator`   bigint      NULL COMMENT '建档人（sys_user.user_id）',
  `space_createtime` bigint     NULL COMMENT '建档时间戳，单位秒',
  `space_updatetime` bigint     NULL COMMENT '最近更新时间戳，单位秒',
  PRIMARY KEY (`space_id`),
  UNIQUE KEY `u_space_no` (`space_no`) COMMENT '车位编号物理唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='停车位台账（设备控制型，删除=禁用）';

-- ----------------------------
-- 停车会话（状态机：0进行中 → 1已结束 | 2已取消，终态不可逆）
-- ----------------------------
DROP TABLE IF EXISTS `park_session`;
CREATE TABLE `park_session` (
  `session_id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '会话id',
  `space_id`            bigint      NOT NULL COMMENT '车位id（park_space.space_id）',
  `space_no`            varchar(32) NOT NULL COMMENT '车位编号冗余（会话期间车位编号，列表展示免 join）',
  `plate_no`            varchar(16) NOT NULL COMMENT '车牌号（应用层统一大写，兼容新能源8位）',
  `session_entry_time`  bigint      NOT NULL COMMENT '入场时间戳，单位秒',
  `session_exit_time`   bigint      NULL COMMENT '出场时间戳，单位秒（进行中为空）',
  `session_cancel_time` bigint      NULL COMMENT '取消时间戳，单位秒（未取消为空）',
  `session_state`       tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-进行中 1-已结束 2-已取消',
  `session_cancel_reason` varchar(256) NULL COMMENT '取消原因（操作端可选填）',
  `session_updatetime`  bigint      NULL COMMENT '最近状态变更时间戳，单位秒',
  PRIMARY KEY (`session_id`),
  KEY `idx_space_state` (`space_id`, `session_state`) COMMENT '入场查重兜底：车位无进行中会话'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='停车会话（一次占位到释放）';

-- ----------------------------
-- 计费规则（M0 单规则：按小时向上取整；多策略/峰谷阶梯属 M2 规则引擎）
-- ----------------------------
DROP TABLE IF EXISTS `fee_rule`;
CREATE TABLE `fee_rule` (
  `rule_id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '规则id',
  `rule_name`       varchar(64) NOT NULL COMMENT '规则名称（如：标准计时收费）',
  `unit_price_fen`  int         NOT NULL COMMENT '单价，单位：分/小时（M0 按小时向上取整计费）',
  `rule_state`      tinyint     NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用（M0 仅一条启用规则）',
  `rule_remark`     varchar(256) NULL COMMENT '说明备注',
  `rule_creator`    bigint      NULL COMMENT '创建人（sys_user.user_id，预置数据为空）',
  `rule_createtime` bigint      NULL COMMENT '创建时间戳，单位秒',
  `rule_updatetime` bigint      NULL COMMENT '最近更新时间戳，单位秒',
  PRIMARY KEY (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='计费规则（M0 单规则）';

-- 预置默认规则（固定 rule_id=1，出场结算读取启用规则；依赖上方 DROP 重建保证幂等）
INSERT INTO `fee_rule`
  (`rule_id`, `rule_name`, `unit_price_fen`, `rule_state`, `rule_remark`, `rule_createtime`)
VALUES (1, '标准计时收费', 200, 1, '按小时向上取整计费（M0 唯一规则）', unix_timestamp(now()));

-- ----------------------------
-- 停车订单（结算时点快照，生成后不可变；金额统一分存储）
-- ----------------------------
DROP TABLE IF EXISTS `park_order`;
CREATE TABLE `park_order` (
  `order_id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '订单id',
  `session_id`        bigint      NOT NULL COMMENT '会话id（park_session.session_id，唯一）',
  `plate_no`          varchar(16) NOT NULL COMMENT '车牌号快照',
  `space_no`          varchar(32) NOT NULL COMMENT '车位编号快照（出场后车位可被复用）',
  `order_entry_time`  bigint      NOT NULL COMMENT '入场时间戳快照，单位秒',
  `order_exit_time`   bigint      NOT NULL COMMENT '出场时间戳快照，单位秒',
  `duration_minutes`  int         NOT NULL COMMENT '停车时长（分钟）',
  `unit_price_fen`    int         NOT NULL COMMENT '结算单价快照，单位：分/小时（规则后续调价不影响历史订单）',
  `amount_fen`        bigint      NOT NULL COMMENT '应收金额（分）',
  `order_state`       tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0-已生成（M0 终态；支付状态 M2 结算域扩展）',
  `order_createtime`  bigint      NOT NULL COMMENT '订单生成时间戳，单位秒',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `u_order_session` (`session_id`) COMMENT '一会话一订单：防出场结算并发触发双订单'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='停车订单（结算快照，不可变）';

-- ----------------------------
-- 停车域菜单注册（固定 menu_id 200-208 避开存量；INSERT IGNORE 幂等）
-- menu_status 0-正常；menu_origin 1-WEB；menu_type 0目录/1菜单/2按钮
-- ----------------------------
INSERT IGNORE INTO `sys_menu`
  (`menu_id`, `menu_origin`, `menu_type`, `menu_name`, `menu_perms`,
   `menu_icon`, `menu_pic`, `menu_def_pic`, `menu_url`, `menu_page`,
   `menu_fid`, `menu_fids`, `menu_ordernum`,
   `menu_createtime`, `menu_updatetime`, `menu_status`)
VALUES
  (200, 1, 0, '停车管理', NULL, NULL, NULL, NULL, NULL, '/parking',
   0, '0', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (201, 1, 1, '车位管理', NULL, NULL, NULL, NULL, NULL, '/parking/space',
   200, '0,200', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (202, 1, 2, '车位查询', 'park:space:list,park:space:info', NULL, NULL, NULL, NULL, NULL,
   201, '0,200,201', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (203, 1, 2, '车位新增', 'park:space:save', NULL, NULL, NULL, NULL, NULL,
   201, '0,200,201', 2, unix_timestamp(now()), unix_timestamp(now()), 0),
  (204, 1, 2, '车位编辑', 'park:space:update', NULL, NULL, NULL, NULL, NULL,
   201, '0,200,201', 3, unix_timestamp(now()), unix_timestamp(now()), 0),
  (205, 1, 1, '停车会话', NULL, NULL, NULL, NULL, NULL, '/parking/session',
   200, '0,200', 2, unix_timestamp(now()), unix_timestamp(now()), 0),
  (206, 1, 2, '会话查询', 'park:session:list', NULL, NULL, NULL, NULL, NULL,
   205, '0,200,205', 1, unix_timestamp(now()), unix_timestamp(now()), 0),
  (207, 1, 1, '停车订单', NULL, NULL, NULL, NULL, NULL, '/parking/order',
   200, '0,200', 3, unix_timestamp(now()), unix_timestamp(now()), 0),
  (208, 1, 2, '订单查询', 'park:order:list', NULL, NULL, NULL, NULL, NULL,
   207, '0,200,207', 1, unix_timestamp(now()), unix_timestamp(now()), 0);
