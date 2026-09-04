# 块记录：P4 管理端 HTTP 层（车位 CRUD + 会话/订单分页 + 权限生效）

> 块循环：设计讨论（3 确认点拍板）→ 实现 → 单测 → 冒烟 → 沉淀（2026-09-05）
> 验证：mvn clean test **72 全绿**（60 + ParkSpaceServiceImplTest 12）；HTTP 冒烟 2 段 **15 项全 PASS**
> 关联：document/plans/M0-停车域最小闭环计划.md P4 行

## 交付清单

| 文件 | 内容 |
|---|---|
| `controller/ParkSpaceController` | 车位 page/info/save/update（无物理删除：禁用=删除） |
| `controller/ParkSessionController` | 会话分页（只读，无写接口） |
| `controller/ParkOrderController` | 订单分页（只读，金额分输出） |
| `service/ParkSpaceService` + Impl | **占用中禁编辑规则** + 编号唯一双保险（预查重 + DB 兜底） |
| `service/ParkOrderService` + Impl | 订单分页（order by id desc） |
| `ParkSessionService.queryPage` | 会话分页（状态筛选 + 车牌/车位模糊） |
| `form/ParkSpaceForm`、`ParkSessionForm`、`ParkOrderForm` | extends CommonForm |
| `vo/ParkSpaceVO` | save/update 共用入参 |
| `ParkSpaceServiceImplTest` | 12 用例：新增 5（成功/重复/空编号/伪造占用拒绝/撞唯一索引兜底）+ 修改 6（成功/占用中拒绝/改号撞重/不存在/伪造占用拒绝/撞索引兜底）+ 分页 1 |

## 关键实现决策（设计稿确认项 + 实测修正）

1. **占用中禁编辑/禁用**：update 前置守卫 `space_state=占用 → 拒绝`——占用中改号破坏会话冗余 space_no 一致性；禁用占用中车位=逻辑矛盾。占用态（1）**不可经管理端置位**（save/update 只允许 0/2），新增/修改传 1 → 业务异常——占用只由入场事务产生
2. **编号唯一双保险**（与入场条件更新同构）：业务层 `selectCount` 预查重（排除自身）快速失败 → `u_space_no` 唯一索引兜底，并发窗口撞索引 `DuplicateKeyException` 捕获转 RRException
3. **@SysLog 只挂写操作**（save/update），查询不记——sys_log 防高频膨胀（壳子 sys 域查询也记是历史习惯，parking 域从新标准开始）
4. **查询直出实体**：parking 实体无敏感字段（对照 sys_user 需脱敏），金额字段 Fen 后缀自带单位注释
5. **Controller 风格对齐壳子**：extends AbstractController + @PreAuthorize + Result/PageUtils/Query；权限串与菜单 200-208 对齐，SecurityConfig 零改动（白名单不含 parking/*）

## HTTP 冒烟结果（真实容器 8200 + dev 库）

- 第 1 段 9 项：401 无 token → 登录 adminManager → 车位分页（权限生效）→ 新增（小写 a-p4-001 归一大写 + creator 落位）→ **重复编号（大写等价命中）500** → 筛选分页 → 详情 → 会话/订单分页
- 第 2 段 6 项：DB 造占用态 → **update 占用中拒绝 500** → 还原空闲 → 禁用（0→2）→ 再启用（2→0）→ 清理测试数据并验证

## 实测修正（设计稿偏差，重要）

1. **adminManager 非 developer 短路**：`roleType = min(role_id)`（SysRoleDao.xml）——adminManager 绑 role_id=2（系统管理员）走 `queryPermsByUserId` 角色-菜单驱动；新菜单未授权 → 全接口 AccessDenied「没有权限」。设计稿假设（developer 全量短路）与库事实不符——adminManager 的权限全量假设**只有绑 role_id=1 的用户成立**（dev@kf，密码未知）
2. **修正动作**：对本地库执行一次性授权 SQL（运营动作，非代码路径）：`INSERT INTO sys_role_menu(role_id,menu_id) SELECT 2,menu_id FROM sys_menu WHERE menu_id BETWEEN 200 AND 208 AND NOT EXISTS(...)`——授权后冒烟全绿。该 SQL 不入 02-parking.sql（sys_role_menu 无唯一索引，重复执行会插脏行；CI 容器按角色数据另配）
3. **冒烟账号事实**：adminManager 密码明文 **admin123**（BCrypt 校验实证）；登录表单字段 **loginname**（非 username）；端口 8200 + context-path /api

## MP 单测新坑

- **@InjectMocks 不注入父类 `baseMapper` 字段**（仅子类显式字段）→ ServiceImpl 的 this.save/getById/page/count 在纯单测 baseMapper=null 直接 MybatisPlusException → **业务方法全部改为显式注入 dao 字段直调**（parkSessionDao.selectById/insert/update/selectPage...），与 ParkSessionServiceImpl 既有风格统一——parking 域 4 个 ServiceImpl 全 dao 直调

## 验证分层

- 单测 12：规则分支（占用中/伪造占用/唯一双保险）——mock 层
- HTTP 冒烟 15 项：权限串生效（403→业务异常文案差异）、大写归一闭环、DB 兜底——真实链路

## 知识点衔接

- 权限模型实证：developer 短路 vs 角色-菜单驱动两条路径的真实分界（min(role_id)=1 才短路）
- @PreAuthorize 权限串与菜单 perms 的一致性：拼错 → AccessDenied 在冒烟暴露（运行时字符串，非编译期）

## 未决（P5）

device-sim module：父 pom 新 module、模拟设备事件上报（调业务 HTTP 入场/出场）、控制指令接收端打日志、设备静态 token 最小鉴权（部署前置项）、与 reason-main 双进程联调
