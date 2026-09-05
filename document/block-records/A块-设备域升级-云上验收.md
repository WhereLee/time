# A 块收口：设备域升级（云上验收通过）

> 状态：✓ 完成（2026-09-06）｜commit 链：847d6e6(A-1) → b946d06(A-2) → efca957(A-3) → dc1851b(A-4) → 557bd72(CI挂载) → 收口提交
> 云上：124.223.36.154 双服务 systemd 部署，A5-ALL-PASS 26 项验收全过

## 1. 范围回顾（六决策落地）

| # | 决策 | 落地 |
|---|---|---|
| 1 | 业务台账与在线台账分离 | `device_online` 只持绑定标识（bind_target），不直连业务表 |
| 2 | 心跳批量聚合上报 | sim 10s 一批 → main 单 SQL IN 批量 UPDATE（online/offline 各一条） |
| 3 | 资产布局 | 300 车位（A 普通 140 / B 普通 130 / C 充电 30 绑桩 10%）+ 6 闸机 + 300 位检 + 30 桩 = 336 设备 |
| 4 | 出入口闸机 + 位检 | 3 出入口 × 入口/出口 = 6 台闸机；位检每车位 1 台 |
| 5 | 手动抬杆闭环（本块做） | 管理端 → device 通道 OPEN_GATE → sim 回执 → 留痕表；会话处置留 B 块 |
| 6 | 故障注入 | sim `/sim/device/offline|online/{deviceNo}` 验证在线态收敛 |

## 2. 小步交付

- **A-1**：db/08 资产 SQL（WITH RECURSIVE 生成 300 车位/336 设备，删旧建新幂等）+ device 域表/枚举/实体/DAO
- **A-2**：sim 规模化（339 台：9 手写 + 4 区段展开）、SimDevice 在线态、心跳批 HeartbeatReporter、故障注入端点
- **A-3**：main 心跳分流 + 差集告警（台账外设备=配置漂移信号）+ 巡检 job（15s 周期 × 30s 超时裁决）+ DeviceHeartbeatController + db/09
- **A-4**：管理端设备面（`parking/device`，避开 `/device` 通道前缀）：台账分页（类型/在线态/绑定筛选）+ 手动抬杆（原因必录审计 + 成败均留痕）+ 抬杆记录查询 + db/10（gate_manual_op + 菜单 400-405 + role2 授权）
- **A-5**：CI 挂载 08/09/10（幂等与表存在入容器验证）、sim 心跳默认开（systemd 无法传参）、云上部署脚本化 + 验收 26 项

## 3. 云上验收（a5-verify.sh，26 项全 PASS）

- V1 资产规模：车位 300 / 桩 30 / 设备 336 / 巡检 job / 菜单 400-405+授权
- V2 台账 HTTP：登录 → 总数 336 → 类型筛（位检 300/桩 30）→ 无 token 401
- V3 心跳在线：336/336 上线稳定 + last_heartbeat 10s 周期刷新实证
- V4 故障注入：SENSOR-B-050 离线 → state=0 → 恢复 → state=1（心跳时刻刷新）
- V5 手动抬杆：下发 success → 留痕 op_result=0 + 车牌 A5TEST01 + 操作人 adminManager + sim 侧 OPEN_GATE 执行痕迹 + 非闸机拒绝 + 空原因拒绝
- V6 巡检裁决：停 sim 45s → 336 全离线（15s job × 30s 阈值收敛）→ 起 sim → 全线上 + 健康

## 4. 工程教训（本块沉淀）

1. **改代码 → `mvn install` → 起服务，三步缺一不可**：两次同根因事故（TraceIdFilter / DynamicContextHolder ClassNotFound）都因多模块 classpath 依赖 .m2 旧 jar。
2. **后台进程必须可观测**：Start-Process Hidden 无日志 = 事故温床；用 terminal buffer 或项目自带 logs/ 分级文件（info/warn/error）归因。
3. **PowerShell 传 mvn 参数整体引号**：含 `=`/`:` 的参数会被拆断（"No plugin found for prefix"）。
4. **管理端路径避开设备通道前缀**：`/device/**` 在 Security 白名单 + DeviceAuthFilter 令牌层，管理端一律不得复用（`parking/device` 方案）。
5. **分页 API 先读样板再写**：项目实际是 `new Query<T>().getPage(MapUtils().put(Constant.PAGE...))`，非 `new Query(form)`。
6. **云上操作脚本化 + 分步可观测**：deploy/verify 双脚本落 document/deploy，重复执行零脑力成本。
7. **sim 心跳默认 true**：systemd 启动无参数注入点，心跳开关必须以配置默认值表达（main 未就绪时心跳失败仅告警抑制）。

## 5. 边界移交（B 块）

- 待绑定态（device_online.bind_state 预留字段未用）
- 位检设备绑定车位（bind 语义当前为出入口/桩）
- 出场事件链（手动抬杆 → 出场会话处置留痕关联，B 块做）
- 设备事件通道扩展（入口/出口/位检事件带出入口标识，通道已预留）
