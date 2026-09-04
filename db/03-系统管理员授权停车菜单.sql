-- ============================================================
-- 03-系统管理员授权停车菜单.sql
-- 执行时机：01 基线 + 02-parking 之后（依赖 sys_menu 200-208 已注册）
-- 语义：系统管理员角色（role_id=2，sys_user_role 种子 adminManager→2）获得停车域菜单权限；
--      非角色菜单驱动的 developer（role_id=1）自动全量，无需授权
-- 幂等性：INSERT ... SELECT ... WHERE NOT EXISTS——重复执行不产生脏行
--   （sys_role_menu 无唯一索引，禁止裸 INSERT 重复执行）
-- 部署：本地库一次性执行过等价语句（P4 冒烟授权），本文件固化入库供新库/CI 使用
-- ============================================================

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, menu_id FROM sys_menu
WHERE menu_id BETWEEN 200 AND 208
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = sys_menu.menu_id
  );
