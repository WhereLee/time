# reason-faster 个人开发框架

基于开源项目 renren-security（MIT License）深度改造的**个人 Java 后端快速开发框架**（Spring Boot 3 现代化版）。

> 原始项目：renren-security（RBAC 权限 + Quartz 调度 + Redis 缓存 + 操作日志），遵循 MIT 协议保留原版权声明；
> 已按个人需求完成现代化改造：**JDK 17 + Spring Boot 3.2 + jakarta + MyBatis-Plus 3.5 + Spring Security 6 + Knife4j 4 + fastjson2**，
> 并完成安全升级：密码哈希 SHA-256 → BCrypt（渐进迁移）、token 生成 MD5 → SecureRandom、Quartz 迁移 spring-boot-starter-quartz（配置 yml 化）。

## 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 LTS | 本地环境 Java 17 |
| Spring Boot | 3.2.12 | javax → jakarta 全量迁移 |
| MyBatis-Plus | 3.5.7 | `mybatis-plus-spring-boot3-starter` |
| Spring Security | 6.2.x | Boot BOM 管理；过滤器链 + STATELESS + @PreAuthorize（见下文） |
| Druid | 1.2.23 | `druid-spring-boot-3-starter`，慢 SQL 监控 |
| Redis | spring-data-redis | `spring.data.redis`（boot3 前缀），缓存开关 `reason.redis.open` |
| Quartz | 2.3.2 | spring-boot-starter-quartz（配置 yml 化），JDBC JobStore 集群模式 |
| Knife4j | 4.5.0 | OpenAPI 3 接口文档（`/doc.html`） |
| fastjson2 | 2.0.53 | 替换 fastjson 1.x（安全 + 性能） |
| Jasypt | 3.0.5 | 配置文件加密 |
| MySQL / PostgreSQL | 8.x / 18.x | 双驱动（默认 MySQL） |

## 目录结构

```
reason-faster
├── pom.xml              父 POM（聚合，单模块）
├── db/reason-faster.sql  建库脚本（含初始账号/角色/菜单）
├── document/            文档体系：块记录/坑位/修复/知识点/roadmap（一问题一文件）
└── reason-main
    └── src/main/java/com/reason
        ├── ReasonApplication.java   启动类
        ├── common               通用：Result/分页/异常/Redis/日志切面/XSS/树/校验
        ├── config               配置：Security/Redis/Druid/MyBatisPlus/Knife4j/Filter
        ├── datasource           自研多数据源（默认单库，按需开启）
        └── modules
            ├── sys              登录/用户/角色/菜单/字典/参数/日志/IP白名单/文件上传
            └── job              Quartz 定时任务 + 执行日志
```

## 快速开始

1. **初始化数据库**（MySQL 8.0）
   ```sql
   CREATE DATABASE reason_faster DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   -- source db/reason-faster.sql
   ```

2. **配置**：`application-dev.yml`（数据源 root/root）、`application.yml`（端口 8200、context-path /api）

3. **启动**
   ```
   cd reason-main && mvn spring-boot:run
   ```

4. **访问**
   - 接口文档：http://localhost:8200/api/doc.html
   - 登录：POST /api/sys/login `{"loginname":"adminManager","password":"admin123"}`
   - 已登录接口需请求头 `token: <token>`

5. **初始账号**（库存数据演示账号，首次使用请修改密码）
   - `adminManager / admin123`：系统管理员（全部菜单权限）
   - `dev@kf`：开发员（最高权限角色，密码已重置不可用，请自行 UPDATE 或走重置密码流程）

## 权限机制（重点）

> 认证授权采用 **Spring Security 6**（由原 Shiro 2 迁移而来）：无状态 token 认证 + 方法级权限注解。
> 认证过滤器自研（`AuthTokenFilter`），token 存库（可吊销/可控过期），未采用 JWT。

- `SecurityConfig`（`config`）：SecurityFilterChain——csrf 关闭、STATELESS、OPTIONS/白名单放行、其余请求需认证、401 统一 JSON（`{code:401}`）。
- `AuthTokenFilter`（`modules/sys/security`）：请求头 `token` 提取 → `AuthService` 校验（token 有效/过期、账号状态、强改密码检查）→ 加载权限写入 `SecurityContext`；`finally` 清理上下文防止线程复用串号。
- `@PreAuthorize("hasAuthority('sys:user:list')")`：接口级权限（`@EnableMethodSecurity`），权限串与 `sys_menu.menu_perms` 一致。
- `@DataFilter`：数据权限过滤（按角色/用户动态拼接 SQL）。
- **密码策略**：BCrypt（默认）；遗留 SHA-256+盐 兼容校验（与 Shiro SimpleHash 位级一致）；登录成功自动重哈希为 BCrypt——渐进迁移，用户无感知。
- 过滤器顺序：`RequestContextFilter(最低) → SecurityFilterChain(含 AuthTokenFilter) → xssFilter → 业务`。

## 新增业务模块 5 步走

1. **建表**：`db/` 新增表结构（或直接 SQL 执行）
2. **实体/Mapper/Service/Controller**：参考 `modules/job` 的现有分层写法（Entity→@TableName、Service→IService、Controller→Result+分页）
3. **注册权限**：`sys_menu` 插入菜单（父级 + 按钮级），`@PreAuthorize("hasAuthority(...)")` 权限串与 menu_perms 一致
4. **初始化**：`sys_role_menu` 给角色分配菜单（初始管理员用 `INSERT SELECT 2,menu_id FROM sys_menu`）
5. **定时任务**（如需要）：实现 `ITask` 接口 + `@Component("beanName")`，后台任务管理页配置 cron

## 日志体系

- **操作日志**：`@SysLog(module=,func=,value=)` + AOP 切面自动落库（`sys_log` 表，含操作人/IP/耗时/异常分类）
- **定时任务日志**：`schedule_job_log` 表，每次执行自动记录
- 已从原框架的 MongoDB 按月存储迁移回 MySQL（单一存储，启动零依赖）

## 测试与 CI

- **单元测试**：核心链路 22 用例——密码编解码（含 Shiro 位级兼容取证向量）、认证过滤器三态、token 服务全分支、登录（BCrypt 渐进升级/尝试锁定）
- **集成测试**（`*IT`，仅 CI 执行）：Testcontainers 起真实 MySQL 8 + Redis，跑完整 HTTP 认证链路（401/登录/带 token 访问/伪造 token）
- **CI**：GitHub Actions（`.github/workflows/ci.yml`），push/PR 触发 `mvn verify`（单测 + 集成测试）

## 已知边界 / Roadmap

见 `document/roadmap.md`（包含"后续再做"待办：腾讯云 COS 对象存储等）。