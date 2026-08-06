# 需求文档（PRD）—— 企业级多租户 AI Agent 工作台

> 产品代号：**AgentScope Workbench**
> 基础框架：AgentScope Java **v2.0.0**（GA，2026-07-10 发布）
> 版本：v1.0-draft
> 状态：评审中

---

## 1. 背景与目标

### 1.1 背景

AgentScope Java v2.0.0 提供了完整的 `HarnessAgent` 体系：文件系统三模式、沙箱隔离、技能市场、双层长期记忆、上下文压缩、Plan Mode、子 Agent、Channel/SSE 事件流、以及一整套生产化分布式组件（`DistributedStore` / `AgentStateStore` / `BaseStore` / `SandboxSnapshotSpec` / `SandboxExecutionGuard` / OTel 观测）。

然而这些能力目前只以 **SDK/代码片段** 形态存在，企业要落地一个可用的 Agent 服务，仍需要自行解决：多租户会话管理、Web 交互协议、Agent 生命周期管理、配置化组装、部署与运维。本项目目标是把官方能力 **产品化封装** 成一个开箱即用的多租户 Agent 服务平台。

### 1.2 目标

- **G1 能力全覆盖**：官方 v2.0.0 文档中 `HarnessAgent` 相关全部能力（见 §3 覆盖矩阵）在平台中可配置、可启用、可演示。
- **G2 多租户就绪**：`(tenantId, userId, sessionId)` 三级隔离，任意租户之间无状态串读。
- **G3 双模式部署**：一套代码两种跑法——本地开发（单机、无外部依赖）/ 生产（Redis + Docker 沙箱、多副本可水平扩展）。
- **G4 开箱即用**：提供管理接口（会话、任务、Plan Mode 操作）与可运行的演示前端（SSE 事件流对接）。
- **G5 可观测**：接入 OpenTelemetry tracing，关键路径可追踪。

### 1.3 非目标（本期不做）

- 不做模型训练/微调；模型只通过 `ModelRegistry` 配置接入。
- 不做 B 端计费、配额计费等商业化模块（预留接口位）。
- 不实现官方内置 Channel 适配器（钉钉/飞书/GitHub/GitLab/企业微信），仅保留扩展位。
- 不开发完整前端产品，仅提供演示页与 SSE 协议文档。

---

## 2. 目标用户与落地场景

### 2.1 用户画像

| 角色 | 说明 | 核心诉求 |
|------|------|---------|
| **平台管理员** | 部署、配置 Agent、管理技能市场 | 配置化、可观测、稳定 |
| **企业租户管理员** | 在平台上开通自己的 Agent 实例 | 租户隔离、技能定制 |
| **业务用户** | 通过 Web 对话使用 Agent | 响应快、结果准、可审查 |
| **研发用户** | 代码辅助、脚本执行、自动化任务 | 沙箱安全、Plan 审批 |

### 2.2 典型场景

**场景 A：研发辅助（R&D Assistant）**
- 用户让 Agent「审查当前 PR 的改动」→ 主 Agent spawn 一个 `reviewer` 子 Agent → 子 Agent 读文件、跑检查脚本 → 结果汇总返回。
- 用户让 Agent「重构 `X` 模块」→ 进入 Plan Mode 写 `plans/PLAN.md` → 用户 HITL 确认 → 执行阶段用 `todo_write` 拆解推进。

**场景 B：知识问答（Knowledge Q&A）**
- 基于团队 Git 技能仓库 + 工作区 `knowledge/` + 长期记忆，回答跨会话积累的问题。
- 长会话场景下依赖：记忆 Flush → 日流水账 → 周期 Consolidation → `MEMORY.md`，配合 `memory_search` / `session_search` 回溯。

**场景 C：自动化执行（Automation Runner）**
- 用户在 Web 上提交「分析这份数据并生成报告」→ Agent 在 **Docker 沙箱**中执行 Python 脚本（`pip install` 依赖随快照保留）→ 产出文件可被下载。
- 平台通过 `SandboxExecutionGuard` 保证同一用户跨副本的沙箱执行互斥。

**场景 D：多租户 SaaS 运营**
- 平台以多副本部署（Redis 状态存储 + 共享 BaseStore），任意副本都能接管同一用户的会话；`IsolationScope.USER` 保证不同用户记忆互不可见。

### 2.3 核心价值

1. **一个入口**：Web 对话统一承载所有 Agent 能力，无需学习多套工具。
2. **安全边界**：不可信执行一律进沙箱，Plan Mode + 权限规则提供人审闸门。
3. **记忆连续**：跨会话、跨副本记住用户上下文与项目知识。
4. **生产可复制**：本地一键起、生产一键配，代码零改动切换。

---

## 3. 官方能力覆盖矩阵（需求追踪基线）

> 以下为 v2.0.0 官方文档功能主题与本文需求的映射，作为验收追踪的基线。

| # | 官方主题 | 官方能力点 | 本文需求条目 |
|---|---------|-----------|-------------|
| 1 | HarnessAgent 构建 | Builder 全配置项、`call()` / `streamEvents()`、RuntimeContext | FR-1 |
| 2 | 文件系统 | 三模式（Local / Remote / Sandbox）、IsolationScope、BaseStore 路由、workspaceProjection | FR-2 |
| 3 | 沙箱 | 五后端（Docker/K8s/E2B/Daytona/AgentRun）、快照恢复、并发守卫、镜像约束、自管实例 | FR-3 |
| 4 | 技能 | 四来源（Git/Nacos/MySQL/Classpath）、四层优先级、自学闭环、沙箱内运行 | FR-4 |
| 5 | 记忆 | 双层记忆、Flush 三触发点、MemoryConfig、记忆工具、后台维护、完全关闭 | FR-5 |
| 6 | 上下文压缩 | 摘要压缩、工具结果卸载、溢出兜底、参数截断、session 查询工具 | FR-6 |
| 7 | Plan Mode | 三件套工具、HITL、权限模式切换、状态持久化、程序化进出 | FR-7 |
| 8 | 子 Agent | 三来源声明、ISOLATED/SHARED、同步/后台、反向通知、任务工具、持久会话、expose_to_user、远程子 agent | FR-8 |
| 9 | Channel | 会话管理、SSE 流式事件、子 Agent 对话、多 Agent 路由、自定义 Channel | FR-9 |
| 10 | 上生产 | DistributedStore（Redis/OSS/MySQL）、AgentStateStore、租户隔离键、OTel | FR-10 |

---

## 4. 功能需求

### FR-1 Agent 核心引擎

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-1.1 | 配置化构建 | 通过配置（YAML/Properties）声明式构建 `HarnessAgent`：name、model、sysPrompt、workspace、steps、temperature 等，禁止硬编码 | P0 |
| FR-1.2 | 多 Agent 注册 | 平台可同时注册多个 Agent（如 `rnd-assistant` / `knowledge-bot` / `ops-runner`），经 Gateway 按 `agentId` 路由 | P0 |
| FR-1.3 | 同步/流式调用 | 提供非流式应答（`call`）与流式事件（`streamEvents`）两套 API | P0 |
| FR-1.4 | RuntimeContext 装配 | 每次调用自动装配 `(userId, sessionId)`，`userId` 采用 `tenantId:userId` 复合格式；`sessionId` 采用 `agentId:sessionId` 复合格式 | P0 |
| FR-1.5 | 系统提示模板 | 支持平台级、租户级、Agent 级三级 sysPrompt 覆盖 | P1 |
| FR-1.6 | 模型路由 | 支持 `provider:model` 字符串配置与 `ModelRegistry` 解析；主模型与记忆/压缩辅助模型可分离 | P1 |

### FR-2 文件系统与多租户隔离

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-2.1 | 三模式切换 | 配置文件一行切换：`LocalFilesystemSpec`（开发）/ `RemoteFilesystemSpec`（生产多副本）/ `DockerFilesystemSpec`（沙箱） | P0 |
| FR-2.2 | IsolationScope 可配 | 支持 SESSION / USER / AGENT / GLOBAL 四档，按 Agent 维度配置 | P0 |
| FR-2.3 | 匿名兜底 | `anonymousUserId` 可配置，匿名调用不聚桶 | P1 |
| FR-2.4 | 远程模式路由 | 生产模式自动按官方路由表将 `AGENTS.md` / `memory/` / `skills/` / `subagents/` / `knowledge/` / `sessions/` / `tasks/` 路由到共享 KV（Redis） | P0 |
| FR-2.5 | 工作区索引 | 远程模式支持 `WorkspaceIndex` 加速 ls/glob/grep | P2 |
| FR-2.6 | 用户级覆盖 | 支持 `workspace/<userId>/skills/` 用户级技能/知识覆盖共用版 | P1 |

### FR-3 沙箱执行（隔离边界）

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-3.1 | Docker 沙箱 | 首版实现 Docker 后端：image、memory、cpu、network、exposedPorts、environment、workspaceRoot 全部可配 | P0 |
| FR-3.2 | 快照策略 | `snapshotSpec` 可配：`NoopSnapshotSpec`（默认）/ `LocalSnapshotSpec`（dev）/ `RedisSnapshotSpec`、`OssSnapshotSpec`、`JdbcSnapshotSpec`（prod）；由 distributedStore 自动注入 | P0 |
| FR-3.3 | 并发守卫 | 生产模式自动注入 `SandboxExecutionGuard`（Redis）；AGENT/GLOBAL scope 下保证同 slot 串行 | P0 |
| FR-3.4 | 镜像约束校验 | 启动时对沙箱镜像做基线检查（sh / POSIX 工具链），不满足给出明确报错 | P1 |
| FR-3.5 | 工作区投影 | `workspaceProjectionEnabled` 与投影根列表可配（AGENTS.md、skills、subagents、knowledge） | P1 |
| FR-3.6 | 后端扩展位 | 预留 K8s / E2B / Daytona / AgentRun 的配置骨架（依赖模块按 profile 引入），本期不激活 | P2 |
| FR-3.7 | 自管沙箱（高级） | 提供 `SandboxContext` 编程式透传能力（外部容器/快照串/共享实例） | P2 |

### FR-4 技能体系

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-4.1 | 多市场接入 | `skillRepository(...)` 可叠加多个市场；按优先级后注册覆盖先注册 | P0 |
| FR-4.2 | Git 市场 | `GitSkillRepository` 接入团队技能仓库（默认轻量远端检查，HEAD 变更才 pull） | P0 |
| FR-4.3 | Classpath 内置 | `ClasspathSkillRepository("skills")` 内置平台基础技能（随 JAR 分发） | P0 |
| FR-4.4 | MySQL 市场 | `MysqlSkillRepository`（读写模式）作为平台统一技能管理入口 | P1 |
| FR-4.5 | 工作区技能 | 支持 `workspace/skills/` 共用 + `<userId>/skills/` 用户覆盖，四层优先级生效 | P0 |
| FR-4.6 | 同名冲突策略 | 冲突时按「全局目录 < 市场 < 工作区共用 < 用户隔离」裁决 | P1 |
| FR-4.7 | 自学闭环（可选） | 可开关：Agent 自写 skill → 审核闸门 → 后台周期整理（本期做开关与工具暴露，审核流做简化版） | P2 |
| FR-4.8 | 沙箱内技能 | 沙箱模式下技能静态资产随 workspace projection 进容器，`<files-root>` 可执行 | P1 |
| FR-4.9 | Nacos 市场 | `NacosSkillRepository`（在线下发/变更订阅）作为扩展位预留，本期不激活 | P2 |

### FR-5 长期记忆

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-5.1 | 默认开启 | 双层记忆（`memory/YYYY-MM-DD.md` + `MEMORY.md`）默认启用 | P0 |
| FR-5.2 | Flush 触发可配 | `flushTrigger`：ALWAYS / THROTTLED(Duration) / NEVER 三档 | P0 |
| FR-5.3 | MemoryConfig 定制 | flushPrompt / consolidationPrompt / model / consolidationMaxTokens / retention 全部可配 | P1 |
| FR-5.4 | 记忆工具 | `memory_search` / `memory_get` 自动注册（关闭记忆工具时移除） | P0 |
| FR-5.5 | 后台维护 | 日流水账归档、MEMORY.md 合并、会话日志清理按保留期自动执行 | P1 |
| FR-5.6 | 完全关闭 | `disableMemoryHooks()` / `disableMemoryTools()` 支持 | P1 |

### FR-6 上下文压缩

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-6.1 | 摘要压缩 | `CompactionConfig`：triggerMessages / triggerTokens / keepMessages / keepTokens / flushBeforeCompact / offloadBeforeCompact | P0 |
| FR-6.2 | 溢出兜底 | 模型返回 `context_length_exceeded` 时自动触发 `recoverFromOverflow` 极端压缩 + 重试一次 | P0 |
| FR-6.3 | 工具结果卸载 | `ToolResultEvictionConfig`：阈值、预览长度、排除工具列表可配 | P1 |
| FR-6.4 | 参数截断 | `TruncateArgsConfig` 对 `write_file` 等大参数做无 LLM 截断 | P2 |
| FR-6.5 | 会话查询 | `session_list` / `session_history` / `session_search` 工具自动注册 | P1 |

### FR-7 Plan Mode（人审闸门）

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-7.1 | 开关与目录 | `enablePlanMode()` + `planFileDirectory`（默认 `plans`）可配 | P0 |
| FR-7.2 | 三件套工具 | `plan_enter` / `plan_write` / `plan_exit` 与只读白名单生效；非白名单调用被拒绝并提示 | P0 |
| FR-7.3 | HITL 确认 | `plan_exit` 走权限系统 ASK 确认；Web 端提供「批准/拒绝」交互 | P0 |
| FR-7.4 | Plan 阶段 shell | `allowShellInPlanMode` 开关（默认关，开启时提示只读约束，编辑工具仍拒绝） | P1 |
| FR-7.5 | 状态持久化 | Plan 状态随 AgentState 持久化，进程重启/副本切换后恢复 | P0 |
| FR-7.6 | 程序化控制 | 管理 API 提供 `enter-plan-mode` / `exit-plan-mode` / 查询 plan 状态 | P1 |
| FR-7.7 | 权限模式切换 | 管理 API 支持按 session 设置 `PermissionMode`（DEFAULT / DONT_ASK / BYPASS），BYPASS 仅限沙箱模式 | P1 |
| FR-7.8 | 任务清单 | `enableTaskList()` + `todo_write` 协作，任务列表经管理 API 可查 | P1 |

### FR-8 子 Agent 编排

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-8.1 | 声明三来源 | 内置 `general-purpose` + 工作区 `subagents/*.md` + 编程式 `SubagentDeclaration` | P0 |
| FR-8.2 | 工作区声明 | 平台内置 3 个演示子 agent：`reviewer`（代码审查）、`researcher`（调研）、`note-taker`（持久会话笔记） | P0 |
| FR-8.3 | 同步/后台 | `timeout_seconds > 0` 同步（默认 30、上限 600）；`= 0` 后台返回 `task_id` | P0 |
| FR-8.4 | 自动反向通知 | 后台任务完成结果作为 `<system-reminder>` 注入下一轮推理 | P0 |
| FR-8.5 | 任务工具注册 | `agent_spawn` / `agent_send` / `agent_list` / `task_output` / `wait_async_results` / `task_cancel` / `task_list` 全量注册 | P0 |
| FR-8.6 | 持久会话 | 声明支持 `persistSession(true)`，按 `(parentSessionId, agentId, label)` 复用实例 | P1 |
| FR-8.7 | expose_to_user | 支持子 Agent 暴露为用户可寻址入口，事件流发出 `SubagentExposedEvent`，Web 端可直达对话（`SubagentGatewayBridge` 接线） | P1 |
| FR-8.8 | 远程子 Agent | `SubagentDeclaration.url(...)` 支持对接远端任务服务器（配置位预留） | P2 |

### FR-9 交互与事件（Spring Boot Web + SSE）

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-9.1 | 发送消息 | `POST /api/v1/chat`（JSON，非流式应答） | P0 |
| FR-9.2 | 流式对话 | `GET /api/v1/chat/stream`（SSE，`TEXT_EVENT_STREAM`），事件类型覆盖：`TextBlockDeltaEvent`、`ToolCallStartEvent`、`ToolResultEndEvent`、`SubagentExposedEvent`、`AgentEndEvent` 等 | P0 |
| FR-9.3 | 会话管理 | `SendOptions` 语义：`userId` 自动建会话；显式 `sessionId` 多会话；`agentId` 路由 | P0 |
| FR-9.4 | 子 Agent 直连 | SSE 接口支持 `subagentId` 参数直达暴露的子 Agent（`sendToSubagentStream` / `sendToSubagent`） | P1 |
| FR-9.5 | 事件协议 | SSE payload 定义统一 JSON 协议（type / id / payload 字段），文档化供前端对接 | P0 |
| FR-9.6 | 多 Agent 路由 | `GatewayBootstrap` 注册全部 Agent，`ChatUiChannel` 统一对外，默认 mainAgent 可配 | P0 |
| FR-9.7 | 演示前端 | 内置一个轻量演示页（原生 JS + SSE），展示流式打字、工具调用、子 Agent 暴露、Plan 审批 | P1 |

### FR-10 生产化与运维

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-10.1 | 分布式一键配置 | `distributedStore(RedisDistributedStore.fromJedis(...))` 自动注入 stateStore + baseStore + snapshot + guard | P0 |
| FR-10.2 | 状态存储选型 | 支持 JsonFile（dev，默认）/ Redis（prod）切换；MySQL 预留 | P0 |
| FR-10.3 | 构建期校验 | 复刻官方校验：Remote 无 stateStore → `IllegalStateException`；沙箱 + 本地 state → warn 日志 | P0 |
| FR-10.4 | 租户隔离键 | 存储键含 `(tenantId, userId, sessionId)`，防跨租户串读 | P0 |
| FR-10.5 | 可观测性 | `OtelTracingMiddleware` + OpenTelemetry SDK 接入；trace 导出（OTLP）可配 | P1 |
| FR-10.6 | 健康检查 | `/actuator/health` 暴露 Agent 与存储依赖健康状态 | P1 |
| FR-10.7 | 配置管理 | 全部 Agent 配置集中在一个 profile 化配置文件中，敏感项（API Key）走环境变量 | P0 |

### FR-11 管理 API（后台能力）

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| FR-11.1 | 会话管理 | 列出/查询/删除会话 | P1 |
| FR-11.2 | 任务列表 | `GET /api/v1/admin/sessions/{id}/tasks`（含 state、subject、owner、依赖） | P1 |
| FR-11.3 | Plan 操作 | `:enter-plan-mode` / `:exit-plan-mode` / `GET plan` | P1 |
| FR-11.4 | 权限模式 | 按 session 设置/查询 `PermissionMode` | P1 |
| FR-11.5 | Agent 状态 | 查询 Agent 注册表、各 Agent 运行状态 | P2 |

---

## 5. 非功能需求

| 编号 | 类别 | 需求 | 验收口径 |
|------|------|------|---------|
| NFR-1 | 性能 | 非流式首响应 P95 < 5s（不含模型推理）；SSE 首事件 < 1s | 压测 50 并发 |
| NFR-2 | 并发 | 单副本支持 50 并发会话；同 session 并发消息公平排队（Gateway 语义） | 压测 |
| NFR-3 | 安全 | API 需鉴权（平台级 API Key）；`BYPASS` 权限模式仅在沙箱下允许；沙箱容器默认无网络或受限网络 | 代码评审 + 测试 |
| NFR-4 | 隔离 | 租户 A 无法通过任何接口读到租户 B 的会话/记忆/文件 | 隔离性测试 |
| NFR-5 | 可靠性 | 进程重启后会话可恢复（状态持久化）；沙箱容器销毁后可从快照恢复 | 故障演练 |
| NFR-6 | 可观测 | 每次调用生成完整 trace（HTTP → Gateway → Agent → 工具 → LLM） | OTel 面板抽查 |
| NFR-7 | 可维护性 | 所有官方能力通过配置开关控制，代码零改动切换 dev/prod | 验收演示 |
| NFR-8 | 兼容性 | Java 17、Spring Boot 4.x、AgentScope 2.0.0 | 构建通过 |

---

## 6. 里程碑规划

| 阶段 | 内容 | 交付物 |
|------|------|--------|
| **M1 骨架（第 1 周）** | 工程依赖、配置模型、Agent 工厂、单 Agent 本地模式 + REST/SSE 接口、演示页 | 可本地对话 |
| **M2 记忆与压缩（第 2 周）** | MemoryConfig/CompactionConfig 接入、记忆工具、session 工具 | 长会话可用 |
| **M3 子 Agent 与 Plan（第 3 周）** | 子 Agent 声明三来源、后台任务、Plan Mode + HITL、任务管理 API | 编排能力完整 |
| **M4 沙箱与技能（第 4 周）** | Docker 沙箱 + 快照、Git/Classpath 技能市场、四层优先级 | 隔离执行可用 |
| **M5 生产化（第 5 周）** | Redis DistributedStore、多 Agent 路由、OTel、校验链路、健康检查 | 生产可部署 |
| **M6 打磨（第 6 周）** | 演示场景端到端、测试补全、文档 | 验收 |

---

## 7. 验收标准

1. **能力矩阵 100% 覆盖**：§3 矩阵中 P0 项全部可演示，P1 项有配置位且可启用。
2. **双模式跑通**：`dev` profile 无外部依赖可完整对话；`prod` profile 依赖 Redis + Docker，多副本下会话/记忆/沙箱可恢复。
3. **多租户隔离**：两个租户并发使用同一 Agent，任意操作互不可见（测试用例覆盖）。
4. **端到端演示**：至少 3 个演示场景（研发审查、知识问答、沙箱执行报告）走通。
5. **测试通过**：单元测试 + 关键路径集成测试（Testcontainers Redis）+ 1 个 E2E。
6. **文档齐备**：本文档 + 设计文档 + 配置说明 + SSE 协议文档。

---

## 8. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| AgentScope 2.0.0 API 与文档示例存在出入 | 编码返工 | M1 先做 API 探针（构建最小可运行 agent），锁定实际 API 后再铺开 |
| 沙箱镜像基线约束（POSIX sh 工具链）不满足 | 文件工具静默失败 | FR-3.4 启动时镜像自检 |
| 本地 Windows 开发环境 Docker 沙箱性能/兼容问题 | 开发受阻 | 沙箱能力在 Linux 环境验证；Windows 本地用 LocalFilesystem 开发 |
| 记忆/压缩的 LLM 额外调用成本 | 成本超预算 | FlushTrigger.THROTTLED 默认 + 小模型跑记忆操作（FR-5.2 / FR-5.3） |
| 子 Agent Plan 模式限制不传播（官方已知缺口） | 只读保证被绕过 | 子 Agent 声明 `tools` 白名单过滤（FR-8.2） |
