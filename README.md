# reason-faster 个人开发框架

基于开源项目 renren-security（MIT License）深度改造的**个人 Java 后端快速开发框架**（Spring Boot 3 现代化版）。

> 原始项目：renren-security（RBAC 权限 + Quartz 调度 + Redis 缓存 + 操作日志），遵循 MIT 协议保留原版权声明；
> 已按个人需求完成现代化改造：**JDK 17 + Spring Boot 3.2 + jakarta + MyBatis-Plus 3.5 + Shiro 2（自研 jakarta Filter）+ Knife4j 4 + fastjson2**，
> 并完成安全升级：密码哈希 SHA-256 → BCrypt（渐进迁移）、token 生成 MD5 → SecureRandom。

## 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 LTS | 本地环境 Java 17 |
| Spring Boot | 3.2.12 | javax → jakarta 全量迁移 |
| MyBatis-Plus | 3.5.7 | `mybatis-plus-spring-boot3-starter` |
| Shiro | 2.0.0 | shiro-core + shiro-spring；**认证过滤器自研**（见下文） |
| Druid | 1.2.23 | `druid-spring-boot-3-starter`，慢 SQL 监控 |
| Redis | spring-data-redis | `spring.data.redis`（boot3 前缀），缓存开关 `reason.redis.open` |
| Quartz | 2.3.2 | 定时任务在线管理 |
| Knife4j | 4.5.0 | OpenAPI 3 接口文档（`/doc.html`） |
| fastjson2 | 2.0.53 | 替换 fastjson 1.x（安全 + 性能） |
| Jasypt | 3.0.5 | 配置文件加密 |
| MySQL / PostgreSQL | 8.x / 18.x | 双驱动（默认 MySQL） |

## 目录结构

```
reason-faster
├── pom.xml              父 POM（聚合，单模块）
├── db/reason-faster.sql  建库脚本（含初始账号/角色/菜单）
├── document/            设计文档、roadmap
└── reason-main
    └── src/main/java/com/reason
        ├── ReasonApplication.java   启动类
        ├── common               通用：Result/分页/异常/Redis/日志切面/XSS/树/校验
        ├── config               配置：Shiro/Redis/Druid/MyBatisPlus/Knife4j/Filter
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

> shiro-web 的 servlet 过滤器基于 **javax.servlet** 编译，无法运行于 Spring Boot 3（jakarta）环境；
> 官方对 Boot3 的 stable starter 尚未发布（最新仅 3.0.0-alpha-1）。
> 因此本框架采用：**shiro-core/spring（认证 Realm、@RequiresPermissions 注解鉴权）+ 自研 jakarta 认证 Filter**。

- `OAuth2Filter`（`modules/sys/oauth2`）：实现 `jakarta.servlet.Filter`，白名单/预检放行 → 提取 token → `subject.login(OAuth2Token)` → 失败返回 `{code:401}`；请求结束清理 `ThreadContext` 防止线程复用串号。
- `OAuth2Realm`：token 查库校验 + 加载用户权限、强改密码检查。
- `@RequiresPermissions("sys:user:list")`：接口级权限，由 `AuthorizationAttributeSourceAdvisor` 拦截。
- `@DataFilter`：数据权限过滤（按角色/用户动态拼接 SQL）。
- 过滤器顺序：`RequestContextFilter(最低) → OAuth2Filter → xssFilter → 业务`（`FilterRegistrationBean` 注册）。

## 新增业务模块 5 步走

1. **建表**：`db/` 新增表结构（或直接 SQL 执行）
2. **实体/Mapper/Service/Controller**：参考 `modules/job` 的现有分层写法（Entity→@TableName、Service→IService、Controller→Result+分页）
3. **注册权限**：`sys_menu` 插入菜单（父级 + 按钮级），`@RequiresPermissions` 权限串与 menu_perms 一致
4. **初始化**：`sys_role_menu` 给角色分配菜单（初始管理员用 `INSERT SELECT 2,menu_id FROM sys_menu`）
5. **定时任务**（如需要）：实现 `ITask` 接口 + `@Component("beanName")`，后台任务管理页配置 cron

## 日志体系

- **操作日志**：`@SysLog(module=,func=,value=)` + AOP 切面自动落库（`sys_log` 表，含操作人/IP/耗时/异常分类）
- **定时任务日志**：`schedule_job_log` 表，每次执行自动记录
- 已从原框架的 MongoDB 按月存储迁移回 MySQL（单一存储，启动零依赖）

## 已知边界 / Roadmap

见 `document/roadmap.md`（包含"后续再做"待办：腾讯云 COS 对象存储等）。