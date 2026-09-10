# Git 与 CI 交接手册（Agent 操作版）

> 受众：在本工作区接手的 agent / 开发者。读完本手册即可独立完成「改动 → 提交 → 推送 → CI 验证」全流程。
> 基线事实（2026-09-11 验证）：remote `git@github.com:WhereLee/time.git`（SSH）、分支 `master`、`gh` CLI 2.96.0 已登录（账号 WhereLee）。状态会变化——以 `git status` / `git log origin/master..HEAD --oneline` 实际输出为准。

## 〇、3 分钟上手

1. `cd C:\Users\lrs\Desktop\py\interview\inteink-faster`（**所有 git 操作必须在仓库根执行**）
2. `git log --oneline -8` 看推进位置；`git status --short` 看工作区；`git log origin/master..HEAD --oneline` 看未推送队列
3. 读最新块记录：`document/block-records/`（按「批次N-主题」找最新）
4. 动手前跑基线：`mvn -pl reason-main clean test`（**clean 必须**——增量编译有"Nothing to compile 却跑旧类"的误判史）

## 一、仓库与环境事实

| 项 | 事实 |
|---|---|
| 仓库根 | `inteink-faster/`（工作区根 `interview/` 本身**不是** git 仓库） |
| 远程 | `origin = git@github.com:WhereLee/time.git`（SSH；本机 key 已配好。新环境先 `ssh -T git@github.com` 验证） |
| 分支 | `master` 单分支线性推进 |
| shell | Windows PowerShell 5.1：**不支持 `&&`**（用 `;`）；含引号嵌套/中文的复杂命令写成脚本文件执行 |
| gh CLI | 已登录：`gh auth status` 应显示 `Logged in to github.com account WhereLee` |
| 工作区根其他内容 | `服务器连接文档.md`、`diag-archive/`、`简历.pdf` 等**不在仓库内**——`git status` 永远看不到它们，不要试图 add |

## 二、提交规范（用户约定，必须遵守）

1. **改动完成自动提交推送**（2026-09-04 用户授权）：每个阶段改动完成后，agent 自行 `git add + commit + push`，不等用户手动操作。push 的作用 = 同步远程 + 触发 GitHub Actions CI。
2. **commit message**：简洁中文 + 类型前缀。

   | 前缀 | 用途 | 真实示例 |
   |---|---|---|
   | `fix:` | 缺陷修复 | `fix: 跨代际事件重放守卫（已见代际集合）+ 契约 §3.3` |
   | `test:` | 测试 | `test: A2 关键类补单测 56 例……——零覆盖三连清底` |
   | `ci:` | CI / 构建配置 | `ci: jacoco 覆盖率防回退门槛（BUNDLE LINE ≥ 0.20）` |
   | `docs:` | 文档 | `docs: B4 补录成本侧观察——洪水日志放大……` |
   | `refactor:` | 重构 | `refactor: 下行指令超时参数化 + JDK HttpClient 连接复用` |
   | `chore(deps):` | 依赖 | `chore(deps): 应用 Dependabot 升级 7 项……` |

   中文 message 直接内联即可（已实测：GitHub 端与本地 `git -c i18n.logOutputEncoding=UTF-8 log` 解码均正常）。
3. **攒批推送纪律**：代码与其测试**同批完成后**、本地 `mvn -pl reason-main clean test` 全绿才 push——分批逐次推会触发多次全量 CI，中间态必红（编译不完备），噪音淹没真问题。纯文档/清理改动可攒批。
4. **密钥零明文**：任何 secret / 口令 / 密钥不进仓库。先例：jasypt 口令走环境变量注入、设备密钥走 DB 导出到本地临时文件（用后删）。
5. **推送时机例外**：当仓库存在"用户明确知情的悬置状态"（如某批次修复待用户拍板推送）时，**先只本地 commit、不 push**，并向用户说明（push 会连带推送整个未推队列）。

## 三、标准提交流程（照抄）

```powershell
cd C:\Users\lrs\Desktop\py\interview\inteink-faster
git status --short                       # 1. 确认改动范围（无意外文件/密钥/大文件）
# 本地验证（按改动类型）：mvn -pl reason-main clean test / 相关剧本
git add <file1> <file2>                  # 2. 精确 add（推荐列文件；确认范围后可 git add -A）
git commit -m "fix: 中文简洁描述"         # 3. 提交
git log -1 --format="%h %s"              # 4. 复核（终端可能显示乱码=GBK 控制台显示层问题，内容本身为 UTF-8）
git push origin master                   # 5. 推送（见下方现象解读）
```

**PowerShell 现象解读（都是正常/假警）**：

- push 时红字 `git : To github.com:...` 是 **假警**——git 把进度写 stderr，PowerShell 将 native stderr 标红。成功判据：看到 `xxxxx..yyyyy  master -> master` 行，且 `$LASTEXITCODE -eq 0`。
- `warning: CRLF will be replaced by LF`——无害（仓库统一 LF 行尾），照常提交。
- `git status` 中中文文件名显示为 `\346\211\271...` 转义——正常（git 默认 `core.quotepath`）。可选：`git config core.quotepath false` 显示原中文（改动本机配置，酌情）。
- add 中文路径时用引号：`git add "document/block-records/批次8-改动留存.md"`。

**push 失败排查**：

| 现象 | 处置 |
|---|---|
| `Permission denied (publickey)` | SSH key 未配/未加载：`ssh -T git@github.com` 验证 |
| `rejected — fetch first`（非快进） | 单人仓库少见；`git pull --rebase origin master` 后重推 |
| 网络超时 | 重试；`git push` 幂等 |

## 四、CI 操作

### 4.1 结构（`.github/workflows/ci.yml`）

- 触发：push 到 `master`（或 PR）
- 两个 job：
  - **build**：`mvn verify`（单测 + 集成测试 `-DexcludedGroups=mq`）；含**双端 Spring Boot 版本一致性机检**、**覆盖率摘要打印**（LINE coverage）、**jacoco 报告 artifact 上传**（30 天）
  - **barrier-sim**：`reason-barrier-sim` 独立构建测试
- 典型耗时：build ≈ 4-5 分钟，barrier-sim ≈ 40 秒
- **一次 push 多个 commit 时只对最新 commit 建 run**（其代码包含全部前置改动）——查 CI 看最新 run 即可

### 4.2 查看与等待

```powershell
gh run list --limit 5                                        # 最近 runs（status: in_progress / completed success）
gh run view <run-id>                                         # 单 run 详情（jobs 明细）
gh run watch <run-id> --exit-status                          # 阻塞等待至结束；$LASTEXITCODE=0 即全绿（推荐收口方式）
gh run view <run-id> --log-failed                            # 失败时只看失败步骤日志
gh run view <run-id> --log | Select-String "LINE coverage"   # 抓覆盖率摘要数字
gh run rerun <run-id>                                        # 整体重跑（IT 环境性抖动时）
```

### 4.3 失败判断纪律

- **看当前 HEAD，别看过往颜色**：历史红 run 是"过去时"——除非其错误代码仍活在当前 HEAD，否则不用修。
- 多 commit 分批推导致的中间态编译红：无需看日志，只看"测试已同步的那次"。
- 覆盖率门槛：`jacoco:check` 要求 BUNDLE LINE ≥ **0.20**（防回退门槛，baseline 21.5%）；跌破需补测试。
- IT（Testcontainers）失败多数为环境抖动：先 `gh run rerun`，持续红再看日志。

## 五、当前悬置状态（2026-09-11，接手先对表）

核验命令：`git log origin/master..HEAD --oneline`（未推队列）+ `git status --short`（工作区）。

- **未推送**：批次8 五项修复 6 个 commit（`353ee06 → f63b431`）+ 本手册所在 commit
- **未提交**：P3 日志节流（LogThrottle 及接入/测试/文档，见 `git status`）
- 全量明细（提交/未提交/库外三档）：`document/block-records/批次8-改动留存.md`
- 建议处置顺序：① 修 `RRExceptionHandler` 注释引用错字（`boot4-incompat` → `boot34-incompat`）② P3 提交 ③ 统一 `git push origin master` ④ `gh run watch` 验证 ⑤（可选，环境可用时）重跑 `_b4_event_flood.jmx` 对比 warn 行数（P3 效果实测）

## 六、范围纪律（什么进库、什么不进）

| 进库 | 不进库 |
|---|---|
| 源码 / 测试 / `document/` 文档 / `contracts/` 契约 | 原始压测 jtl（体积）、`dump.rdb`、密钥文件 |
| `scripts/verify/` 剧本（jmx/ps1）与统计证据（`_out.txt`） | `logs/`、`target/`（.gitignore 已覆盖） |
| `db/` 增量迁移 SQL | 工作区根文件（`服务器连接文档.md`/`diag-archive/` 等，不在仓库） |

入库前自检：`git check-ignore <path>`（exit 0 = 已忽略，可安全不动）；`git ls-files | Select-String "<name>"`（确认没被跟踪）。

## 七、速查表

| 命令 | 用途 |
|---|---|
| `git status --short` | 工作区状态（M=改 / ??=新 / 无=干净） |
| `git log --oneline -8` | 最近提交 |
| `git log origin/master..HEAD --oneline` | 未推送 commit 队列 |
| `git show <hash> --stat` | 单 commit 改动面 |
| `git add "路径/中文名.md"` | 精确暂存（中文加引号） |
| `git commit -m "fix: 描述"` | 提交（中文前缀规范） |
| `git push origin master` | 推送（红字假警，认 `master -> master` 行） |
| `gh run list --limit 5` | CI 最近运行 |
| `gh run watch <id> --exit-status` | 等待 CI 结果（0=绿） |
| `gh run view <id> --log-failed` | 失败日志 |
| `git check-ignore <path>` | 检查是否被忽略 |
| `git ls-files \| Select-String "<name>"` | 检查是否已被跟踪 |
| `mvn -pl reason-main clean test` | 平台全量单测（基线验证） |
| `mvn -pl reason-main clean test "jacoco:check@check"` | 单测 + 覆盖率门槛校验 |
