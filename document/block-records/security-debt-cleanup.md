# 块记录：安全债务清零（@DataFilter 审计 / 日志脱敏 / session 死重清理 / 豁免 URI 配置化）

> 块循环：2026-09-04
> 验证：mvn clean test 27 用例全绿 + 启动回归（登录/nav/info code 0）

## B1 @DataFilter 注入审计（纯审计，零代码改动）

**结论：零注入面。** 审计方法学与证据链：

1. 拼接点盘点：7 个 `.apply(form.getSqlFilter()...)`（MyBatis-Plus QueryWrapper SQL 片段直拼）——菜单 3/角色 2/用户 2
2. 切面覆盖：7 处全部带 @DataFilter 注解 → DataFilterAspect @Before **无条件 setSqlFilter 覆盖**（用户传入值无法存活）
3. 拼接内容：全部为服务端推导——DB 查询的 Long 角色/菜单/用户 ID 集合 + 认证上下文 userId，形态 `id in (1,2,3)`，无用户可控输入直达
4. 无 @DataFilter 方法无 apply——无遗漏消费点
5. knife4j 接口参数说明全部 ignore sqlFilter——文档无误导

**审计过程两次假阳性记录**：mapper XML 零 `${}` 引用 → 一度误判"数据权限空转"；实为查询走 MP 自动 SQL（Wrapper），XML 无消费属正常。教训：功能消费点的搜索范围要覆盖框架风格差异（XML vs 自动 SQL）。

## B2 日志脱敏

- 新增 `SensitiveDataMasker`（common/utils）：JSON 递归遍历，字段名含敏感词根（password/pwd/token/salt，大小写不敏感）即掩码 `***`
- 集成点：SysLogAspect 的 `log_params`（请求参数）与 `log_return`（返回体）落库前脱敏——sys_log 表不再明文存密码/token
- 取舍：词根子串匹配而非精确名单——**覆盖性优先于精确性**（漏网密码字段 > 误伤普通字段）；误伤面由词根长度控制（token 会命中含 token 的日志字段，可接受）
- 单测 5 用例：顶层/嵌套/数组递归/大小写/空输入防御/操作日志数组形态

## B3 @EnableRedisHttpSession 死重清理

- 证据：认证为 STATELESS token；SessionRepositoryFilter 挂在 servlet 链上但无 session 消费者（SecurityConfig STATELESS 后不创建 HttpSession）
- 动作：CorsConfig 移除 @EnableRedisHttpSession + CookieSerializer bean（该类回归纯 CORS 职责）；pom 删 spring-session-data-redis 依赖
- 顺带删除 reason.shiro.redis 死配置（Shiro 迁移残留）

## B4 强改密码豁免 URI 配置化

- AuthServiceImpl 硬编码常量 → `reason.security.change-password-exempt-uris`（yml 逗号分隔字符串）
- 启动回归验证绑定生效（登录 + 两个豁免 URI 接口 code 0）
- 踩坑记录：@Value 无法绑定 yaml 列表形式 → pitfalls/value-yaml-list-binding.md
- AuthServiceTest 适配：@BeforeEach ReflectionTestUtils 注入字段（@Value 不经 @InjectMocks）
