# 升降杆样例 × reason-faster 基础框架

> **是什么**：一个面向**物联网设备接入**的端到端样例工程——50 台虚拟升降杆（独立模拟器进程）接入平台，覆盖
> per-device HMAC 设备认证、可靠事件通道（RocketMQ 默认 + HTTP 降级三态）、指令闭环状态机（seq 幂等 + QUERY_STATE 对账）、
> 告警全生命周期（去重/限速/合并/自动恢复）、traceId 全链路可观测、Quartz 集群双实例实证、50 台量级回归与红队式安全治理。
> 底层是 **reason-faster 基础框架**（renren-security MIT 深度改造：JDK 17 + Spring Boot 3.5 + Spring Security 6 + MyBatis-Plus + Quartz 集群）。

## 文档导航（按阅读顺序）

| # | 文档 | 一句话 |
|---|---|---|
| 1 | [运行与演示手册](document/升降杆样例-运行与演示手册.md) | 怎么跑、怎么演示（含剧本命令与踩坑前置） |
| 2 | [架构图集](document/升降杆样例-架构图集.md) | 六张图看懂拓扑/指令闭环/双通道/告警/状态机/事故定位 |
| 3 | [设备协议 v2](contracts/PROTOCOL-V2.md) | 双端契约（语义层+传输层一次定稿） |
| 4 | [红队评估与成长路线](document/plans/升降杆样例-红队评估与成长升级路线.md) | T1-T20 问题谱 × 修复史 × 全阶段落地标注 |
| 5 | [终稿规格](document/plans/升降杆样例-终稿规格.md) | 五批次执行序与全局 DoD |
| 6 | [块记录](document/block-records/) | 每批次"做了什么/取舍/验证"+证据索引 |
| 7 | [验证剧本](scripts/verify/README.md) | 批次2-7 剧本与运行证据（可复跑） |
| 8 | [云服务器部署手册](document/deploy/云服务器部署手册.md) | 单机六进程全量复现（含验收清单） |
| 9 | [容量画像与排障实录](document/knowledge/heartbeat-event-capacity-profile.md) | JMeter 实测：限流桶边界 / 80rps 画像 / JVM 采样 / 生命线隔离 |

## 样例能力速览

| 能力 | 关键点 | 落档 |
|---|---|---|
| 设备认证 | per-device 32hex 密钥 + HMAC（上行验签/下行签名双通道同权；secret 仓库零明文） | 红队 0.5 / S2-S3 |
| 可靠事件 | MQ 单通道默认（单队列保序/幂等吸收/死信）+ HTTP 降级三态零代码切换 | 批次2-3 / 契约 §7 |
| 指令闭环 | sim seq 幂等（执行/拒绝双线）+ bootId/eventSeq 序守卫 + 超时 QUERY_STATE 实况对账 | 0.1/0.6 / S6 |
| 告警治理 | 去重(SETNX+DB 兜底) + 限速 + 批量离线合并 + 确认与自动恢复闭环 | 批次4 |
| 可观测性 | traceId 全链路贯穿（HTTP+MQ property）+ 结构化日志 + 任务看护 + 第0档指标端点 | 批次1/4 |
| 调度 | Quartz 集群双实例实证（互斥 + 故障接管，无补跑/无双跑） | 阶段1 |
| 安全治理 | 配置安全（fail-fast/ENC/jasypt/CORS）+ 认证前置（双维度锁定）+ 上行入口令牌桶限流 | 批次5 S1-S9 |
| 数据治理 | 时间戳毫秒化（破坏性变更含幂等迁移）+ 90 天表保留清理（双时域 cutoff） | 批次5 D-H/D-G |

> 完整的问题发现与修复史（20 洞 + 批内缺陷）：见红队文档与 `document/{block-records,pitfalls,fixes,knowledge}/`。

## 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 LTS | 本地环境 Java 17 |
| Spring Boot | 3.5.16 | 双端（main/sim）严格对齐；javax → jakarta 全量迁移 |
| MyBatis-Plus | 3.5.7 | `mybatis-plus-spring-boot3-starter` |
| Spring Security | 6.5.x | Boot BOM 管理；过滤器链 + STATELESS + @PreAuthorize（见下文） |
| Druid | 1.2.28 | `druid-spring-boot-3-starter`，慢 SQL 监控 |
| Redis | spring-data-redis | `spring.data.redis`（boot3 前缀），缓存开关 `reason.redis.open` |
| Quartz | 2.5.2 | spring-boot-starter-quartz（配置 yml 化），JDBC JobStore 集群模式 |
| Knife4j | 4.5.0 | OpenAPI 3 接口文档（`/doc.html`） |
| fastjson2 | 2.0.65 | 替换 fastjson 1.x（安全 + 性能） |
| Jasypt | 3.0.5 | 配置文件加密 |
| MySQL / PostgreSQL | 8.x / 18.x | 双驱动（默认 MySQL） |

## 目录结构

```
reason-faster
├── pom.xml              父 POM（聚合 reason-main）
├── db/  建库与增量 SQL（reason-faster.sql 基线；02-09 barrier 增量=文件名序即依赖序，幂等）
├── document/            文档体系：块记录/坑位/修复/知识点/roadmap/部署（一问题一文件）
├── scripts/verify/      批次2-7 验证剧本与证据（与 block-records 一一对应）
├── reason-barrier-sim/  升降杆模拟器（独立模块不聚合父 pom；启动见 document/升降杆样例-运行与演示手册.md）
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

3. **启动**（T16 起平台必须显式 profile；无 profile=数据源缺失 fail-fast，是预期不是故障）
   ```
   mvn -pl reason-main spring-boot:run -Dspring-boot.run.profiles=dev
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

- **单元测试**：176 用例（平台，批次7 时点，全绿）+ sim 独立测试——密码编解码（含 Shiro 位级兼容取证向量）、认证过滤器三态、token 服务全分支、登录防护（BCrypt 渐进升级/账号×IP 组合锁/伪造 XFF 被忽略）、设备域全链路（指令下发/对账巡检/通道验签三类行覆盖 100%）
- **集成测试**（`*IT`，仅 CI 执行）：Testcontainers 起真实 MySQL 8 + Redis，跑完整 HTTP 认证链路（401/登录/带 token 访问/伪造 token）
- **端到端剧本**：`scripts/verify/`（批次2-7，本地执行、证据落档——与 block-records 一一对应）；批7 引入 JMeter 压测剧本（限流桶边界 / 80rps 容量画像 / 事件洪水生命线隔离，见 knowledge/容量画像）
- **CI**：GitHub Actions（`.github/workflows/ci.yml`）双 job——`build`（单测 + 集成测试，`-DexcludedGroups=mq`；输出 LINE 覆盖率摘要 + jacoco 报告 artifact + 双端 Spring Boot 版本一致性机检）+ `barrier-sim`（模拟器独立构建测试）；MQ 端到端降级声明见 block-records/阶段2-实施记录 §四

## 已知边界 / Roadmap

- 边界（既定取舍）：多租户/账务（主线立项块）、前端（纯后端样例）、TLS（物理单机；公网暴露形态需反代+TLS——见部署手册）、多站点、设备数 >50、注册制动态令牌（留主线）
- 告警触达（通知渠道）为产品边界决策项：当前闭环止于管理端列表
- "后续再做"待办索引：见 `document/roadmap.md` 与 `document/roadmap/`