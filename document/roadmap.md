# Roadmap / 待办清单（"后续再做"记录）

> 本文件记录**明确说过"后续再做/先删掉后面补"**的功能点，留着备用。
> 原则：不现在实现，不因为没做而遗漏——想启用时按这里来。

## 1. 腾讯云 COS 对象存储（后续再写）⭐

- **状态**：已删除（原框架的 oss 模块：CloudStorageService 抽象 + 七牛/阿里/腾讯三实现全部移除）
- **背景**：本地有腾讯云账号，购买服务器时已开通 COS 存储资源；数据库里原公司阿里 OSS 的 bucket/密钥已清理
- **后续方案**（参考原代码结构）：
  1. `modules/oss`：`CloudStorageService` 抽象 + 简单工厂 `OSSFactory`（保留原来的设计）
  2. 实现只做**腾讯云 COS 新版 SDK（5.x）**：`putObject` 上传、URL 生成（注意：原代码是 4.x 老 API `UploadFileRequest`，需按新 SDK 重写）
  3. 双通道：本地磁盘（默认）+ COS（可配），`application.yml` 加开关
  4. 依赖：`com.qcloud:cos_api:5.6.x`（新版）
- **触发条件**：做第一个需要存图片/附件/导入导出文件的实际业务时

## 2. 多数据源（保留自研，默认单库）

- **状态**：自研 `@DataSource` + AOP 切面保留（`datasource/`），当前**默认单数据源**
- **后续**：中小项目 95% 用不上；真需要时放开 `application-dev.yml` 中注释的 `dynamic` 配置块
- 备选：`dynamic-datasource-spring-boot3-starter`（社区方案，文档全），YAGNI 暂不引入

## 3. Shiro 官方 Boot3 starter（跟踪）

- **现状**：shiro 官方 `shiro-spring-boot-web-starter` 对 Spring Boot 3/Jakarta 的支持最高版本为 `3.0.0-alpha-1`（未稳定），故自研 jakarta Filter（`OAuth2Filter`）
- **触发**：官方发布 stable 版后，可评估替换为官方 starter，简化 `OAuth2Filter/ShiroConfig` 的自研代码

## 4. MongDB / 短信 / 企业微信 / 微信（已删除，不计划恢复）

- 日志已迁回 MySQL；短信/企微/微信所需公司私有 SDK 已删除，如需第三方能力（如短信验证码）到时接入新供应商 SDK（不在本框架预置）

## 5.（可选）代码生成器

- 用户明确不采用（个人项目手写 CRUD 更快、模板风格易过时），如未来改变主意：参考 RuoYi generator 思路，输出 Spring Boot 3 + jakarta 风格代码

## 6. Spring Security 学习路线（独立项目，不进本框架）

- 本框架保留 Shiro；Spring Security 6 作为独立学习项目实践（认证授权体系不同，避免混入导致维护成本翻倍）