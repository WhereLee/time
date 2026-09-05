-- ============================================================
-- 05-系统管理员授权充电菜单.sql
-- 执行时机：04-charging 之后（依赖 sys_menu 300-310 已注册）
-- 语义：系统管理员角色（role_id=2）获得充电域菜单权限（对齐 03 停车授权模式）
-- 幂等性：INSERT ... SELECT ... WHERE NOT EXISTS（sys_role_menu 无唯一索引，禁止裸 INSERT 重复执行）
-- ============================================================

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, menu_id FROM sys_menu
WHERE menu_id BETWEEN 300 AND 310
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = sys_menu.menu_id
  );
