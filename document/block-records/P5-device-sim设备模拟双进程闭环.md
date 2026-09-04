# 块记录：P5 device-sim 设备模拟 module（双进程闭环）

> 块循环：设计讨论（4 确认点拍板：聚合继承解耦/设备通道单密钥/指令 fire-and-log/手控+剧本）→ 实现 → 单测 → 双进程冒烟 → 沉淀（2026-09-05）
> 验证：reactor mvn clean test **83 全绿**（main 80 + device-sim 3）；双进程 HTTP 冒烟 **25 项全 PASS**（含 OPEN_GATE 放行指令在 sim 侧日志实证）
> 关联：document/plans/M0-停车域最小闭环计划.md P5 行

## 交付清单

### reason-main（设备接入面，业务事务零改动）
| 文件 | 内容 |
|---|---|
| `parking/security/DeviceAuthFilter` | `/device/**` 设备通道鉴权：`X-Device-Token` header 比对 yml `reason.device.access-token`；**安全默认：未配置即拒绝一切**；非设备路径透传 |
| `parking/controller/DeviceParkingController` | entry/exit/cancel 三事件：校验后复用 ParkSessionService（P2/P3 已验事务）；exit 结算成功 → fire 放行指令 |
| `parking/service/DeviceCommandClient` | reason-main → device-sim `/cmd/exec`：2s 双超时，**失败仅告警不抛**（账务不依赖设备反馈契约） |
| `SecurityConfig` | ANON_PATHS += `/device/**`（安全由 DeviceAuthFilter 承担）；filter 注册于 AuthTokenFilter 后 |
| yml | `reason.device.access-token` / `reason.device.sim-base-url` |

### reason-device-sim（新 module，独立最小依赖）
| 文件 | 内容 |
|---|---|
| `DeviceSimApplication` | 8300 端口；@ConfigurationPropertiesScan |
| `config/DeviceSimProperties` | access-token / parking-api-base-url / devices[deviceNo,spaceNo,deviceType] / auto{enable,staySeconds,intervalSeconds} |
| `model/SimDevice` | 运行态内存机：idle ↔ 会话中（volatile 会话句柄/车牌/入场时间） |
| `reporter/ParkingEventReporter` | 携设备令牌上报 entry/exit/cancel；响应异常即抛（调用方记日志留失败现场） |
| `controller/DeviceCmdController` | `/cmd/exec` 收控制指令打 INFO 日志模拟执行（OPEN_GATE/LOCK/UNLOCK） |
| `controller/SimController` | `/sim/event/{deviceNo}/{event}` 手控单步触发；`/sim/devices` 运行态 |
| `sim/SimEngine` | 自动剧本（@Scheduled，auto.enable=false 空转）；Plates 车牌池公共取牌 |

## 关键决策（设计稿拍板项）

1. **聚合与继承解耦**：父 pom `<modules>` 聚合（reactor 一次构建 CI 双跑），device-sim 的 `<parent>` 直指 spring-boot-starter-parent——父级全局依赖（mysql/druid/mybatis/redis 全家桶）不带入；若继承父级，无数据源配置启动即失败（Spring Boot 默认行为），且 Maven 子 pom 无法排除父级全局依赖。父 pom 依赖管理现代化重构（全量迁 dependencyManagement）留作工程优化项
2. **设备通道人机分离**：/device/** 走 DeviceAuthFilter 单密钥鉴权，不占 sys_user token 体系；M0 单密钥最小鉴权（泄露仅能以设备身份上报业务事件，进不了管理端）
3. **指令 fire-and-log**：exit 结算成功后 Controller 层同步下发放行指令（2s 超时，失败 log.warn）——账务正确性不依赖设备反馈；指令重试/离线补偿归 M2 设备治理
4. **设备持会话句柄**：entry 响应带回 sessionId，设备 exit/cancel 携带——服务层零新增查询
5. **默认手控 + 自动剧本开关**：M0 联调用 /sim/event 单步；剧本演示用（绑定车位空闲即入场/停满出场）

## 双进程冒烟（8200 ↔ 8300，25 项）

401 无/错 token → DEV-001 entry（sim 携 token）→ main 会话建立（管理接口可见）→ 重复 entry 设备忙（sim 侧防御）→ exit → 订单生成 + sim 设备态回 IDLE + **sim 日志「设备执行控制指令：cmd=OPEN_GATE, spaceNo=A-001」实证** → DEV-002 cancel → 会话 state=2 → DEV-003 entry 后管理端 update → 占用中拒绝（跨链路规则生效）→ 数据清理

## 实现中的坑（记录）

- Write 工具对 device-sim 新目录连续误报失败但文件实际已写（半写文件带残留空壳类：ParkingEventReporter 尾部出现 `public class ParkingEventReporter {}` 空壳重复声明 → 编译报 duplicate）→ **新建目录文件后必须全量扫描校验（type decls count / 空壳正则），不信任 create 报错信息**
- DeviceAuthFilter 首版 writeJson 依赖 `HttpContextUtils.getOrigin()`（RequestContextHolder）→ 纯单测 NPE → 改为从 request 取 Origin header 回显（更稳且 CORS 语义正确）
- Python 冒烟脚本 URL query 中文必须 urllib.parse.quote 编码
- 冒烟脚本误把"管理端占用中禁改"验证写成 DB 强改占用态 → exit 释放判官正确回滚暴露（反证机制正确）；修正脚本为真实管理端调用

## 未决（P6 收口）

- 云服务器部署：双进程（reason-main 8200 + device-sim 8300）+ 生产配置（token/sim 地址走内网）+ M0 验收清单 7 条全过
- HTTP 层 IT 化评估（AuthIT 模式扩展业务接口，CI 是否纳入设备链路）
- 父 pom 依赖管理现代化（dependencyManagement 重构）排期
