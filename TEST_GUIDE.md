# AgentScope Workbench 阶段测试指南

> 适用版本：当前 main 分支（AgentScope 2.0.0 / Spring Boot 4.1）
> 面向对象：测试工程师
> 文档日期：2026-08-07
> 阶段状态：**M1~M3 已验收通过**（2026-08-07，非流式/SSE 流式对话、管理面板、模型选择、HITL 待确认等全部通过；SSE 流中断已修复，见第七节）；**M4 沙箱与技能推进中**（Docker 沙箱/快照/技能市场，见第六节）

## 一、测试入口与环境准备

| 项目 | 说明 |
|---|---|
| 页面地址 | `http://localhost:8080/`（`static/index.html`，SSE 演示工作台） |
| 服务端口 | 8080，未配置 context-path |
| 启动 Profile | `dev`（`application-dev.yml`） |
| 认证头 | `X-API-Key: _default`（dev 默认值），另需 `X-Tenant-Id`、`X-User-Id` 请求头 |
| 密钥来源 | 环境变量 `DEEPSEEK_API_KEY` 或配置 `workbench.deepseek.api-key`（配置优先于环境变量），`workbench.deepseek.base-url` 可覆盖端点，默认 `https://api.deepseek.com` |

启动命令（无 DEEPSEEK_API_KEY 时可用配置注入，二选一）：

- 环境变量方式：`setx DEEPSEEK_API_KEY <key>` 后**新开终端**再启动
- 配置注入方式：启动前设置 `SPRING_APPLICATION_JSON={"workbench":{"deepseek":{"api-key":"<key>"}}}`

## 二、对话功能（核心）

| 功能点 | 入口 | 预期行为 |
|---|---|---|
| 非流式对话 | `POST /api/v1/chat`（JSON：message/sessionId/agentId） | 返回完整回复，含 response/sessionId/agentId；空 message 返回错误；300 秒超时 |
| SSE 流式对话 | `GET /api/v1/chat/stream?message=&agentId=&sessionId=&subagentId=` | `text/event-stream` 推送事件：`TEXT_BLOCK_DELTA`（增量文本）、`TOOL_CALL_START`、`TOOL_RESULT_END`、`SUBAGENT_EXPOSED`、`AGENT_END`、`DONE` |
| 工具调用可视化 | 页面对话中触发工具时 | 右侧消息区出现工具卡片，状态流转：执行中(黄) → 成功(绿)/失败(红)，可点击折叠 |
| 子 Agent 直连 | 对话暴露子 Agent 后点击“打开 Tab” | 新 Tab 建立，Tab 内消息带 `subagentId` 直连子 Agent |
| 多 Tab 会话 | 页面 Tab 栏 | 主会话 + 多个子 Agent Tab 并行，各自独立会话 |

## 三、模型与 Agent 选择

| 功能点 | 入口 | 预期行为 |
|---|---|---|
| 模型下拉选择器 | 顶栏“模型”下拉，数据源 `GET /api/v1/admin/models` | 列出配置的模型（DeepSeek V4 Pro / V4 Flash），选中后联动 agentId |
| Agent 选择 | 顶栏“Agent”下拉 | 主 Agent 默认；双 Agent 可切换：`rnd-assistant-pro`（deepseek-v4-pro）、`rnd-assistant-flash`（deepseek-v4-flash） |
| Agent 列表查询 | `GET /api/v1/admin/agents` | 返回主 Agent 名、全部 Agent 集合、数量 |
| 会话路由 | 页面输入租户/用户/会话后发送 | 同一用户同一会话可续聊（`agentId:tenantId:userId` 复合会话键） |

## 四、管理面板（右侧栏）

| 功能点 | 入口 | 预期行为 |
|---|---|---|
| Plan Mode 查看 | `GET /api/v1/admin/sessions/{tenant}/{userId}/{sessionId}/plan` | 返回是否激活 + 当前计划文件 |
| 进入/退出 Plan Mode | `POST ...:enter-plan-mode` / `POST ...:exit-plan-mode` | 返回 ok + 提示文案，面板状态实时刷新 |
| 权限模式查询 | `GET .../permission-mode` | 返回 DEFAULT/DONT_ASK/BYPASS |
| 权限模式设置 | `PUT .../permission-mode`（body：mode） | 支持 DEFAULT/DONT_ASK/BYPASS；**BYPASS 仅限沙箱文件系统 Agent，否则 400**；非法模式 400 |
| 任务列表 | `GET .../tasks` | 返回会话内任务（id/主题/描述/状态/负责人/依赖/创建时间），页面按状态着色（待处理/进行中/完成/失败） |
| 会话列表 | `GET /api/v1/admin/sessions/{tenant}/{userId}` | 返回用户在某 Agent 下的全部会话 ID |

管理 API 路径说明：`{tenant}` 与 `{userId}` 为复合键中的分段，页面侧默认租户/用户见顶栏输入框。

## 五、关键配置项（application.yml / application-dev.yml）

- `workbench.api-key`：管理 API 认证密钥（dev 为 `_default`）
- `workbench.deepseek.api-key` / `base-url`：DeepSeek 密钥与端点（环境变量兜底）
- `workbench.agents`：Agent 定义（name/label/model/sys-prompt/workspace/steps/filesystem/sandbox），支持 `${LLM_MODEL_PRO:...}`、`${LLM_MODEL_FLASH:...}` 环境变量覆盖模型
- `workbench.store.type`：状态存储类型（json-file）

## 六、M4 沙箱与技能验收（需 Docker/Linux 环境）

> 本阶段本地（Windows dev）保持 LOCAL 模式零变化；沙箱端到端项在具备 Docker 的 Linux 环境执行。
> SANDBOX 演示 Agent 配置段已作为注释示例放在 `application.yml` 末尾，启用方式见其注释。

### 6.1 沙箱配置解析（FR-3.1，任意环境）

- 启动日志确认 Agent 构建信息含 `snapshot=` 与 `projectionRoots=`（如 `LOCAL` 与 `[AGENTS.md, skills, subagents, knowledge]`）
- `application.yml` 新增字段均可绑定：`network`/`snapshot-base-path`/`guard-enabled`/`check-on-start`/`workspace-root`/`exposed-ports`/`environment`/`additional-run-args`/`projection-roots`，未知字段不报错（Spring 宽松绑定）

### 6.2 镜像基线校验（FR-3.4）

| 场景 | 预期行为 |
|---|---|
| 无 Docker 环境启动 SANDBOX Agent | 启动报明确错误（含镜像名与缺失工具提示），提示 `sandbox.check-on-start: false` 可绕过 |
| `check-on-start: false` | 跳过校验，启动正常 |
| Docker 可达且镜像含 sh/ls/cat | 启动正常，进入沙箱模式 |

### 6.3 快照与守卫（FR-3.2/FR-3.3）

- `snapshot-type: LOCAL`：沙箱首次执行后生成 `snapshot-base-path`（默认 `.agentscope/snapshots`）目录；`NONE` 不生成；`REMOTE` 启动日志告警「由 M5 DistributedStore 注入」并回退 Noop
- `guard-enabled: true`：启动日志告警「Redis 守卫由 M5 DistributedStore 注入，当前使用 noop」；`false` 无告警（默认）

### 6.4 技能市场与自学闭环（FR-4.1/FR-4.3/FR-4.7）

- 启动日志含 classpath 技能市场注册信息；配置 `git` 市场但无 `git-url` 时告警跳过（`agentscope-extensions-skill-git` 构件未引入，FR-4.2 待 M5/扩展构件）
- 内置演示技能 `prd-summarizer`（`resources/skills/`）：对话中要求「摘要 PRD」可触发，日志可见技能加载
- `skill-manage-enabled: true`：启动日志出现「技能自学闭环已开启」；默认 `false` 无变化
- 沙箱内技能（FR-4.8）：SANDBOX Agent 对话触发技能，容器内 `<files-root>` 可见投影进来的 `skills` 文件（`projection-roots` 默认含 `skills`）

## 七、已知问题（务必知悉）

- ~~**SSE 流中断（未修复）**~~：**已修复（sse05）**。`GET /api/v1/chat/stream` 现为通过项——`SseEventMapper.toStream` 的 `onErrorResume` 对流内异常下发 `error` 事件后优雅结束，不再产生 `ERR_INCOMPLETE_CHUNKED_ENCODING`；`DONE` 事件始终收尾。
- 子 Agent 需对话中由主 Agent 触发暴露后才出现，属动态能力，非固定入口。
- Git 技能市场（FR-4.2）依赖 `agentscope-extensions-skill-git` 扩展构件（Maven 中央仓库未发布），当前维持配置位 + 告警，不阻塞其他能力。
- 沙箱端到端（快照恢复/沙箱内技能）依赖 Docker 环境，Windows 本机不验证，验收口径见第六节。

## 八、建议测试顺序

1. 启动服务（确认无 `DEEPSEEK_API_KEY 未配置` 报错）
2. `GET /api/v1/admin/agents`、`/models`、页面顶栏下拉加载
3. 非流式 `/chat` 全链路（双 Agent 各发一条）
4. SSE 流式 `/chat/stream`（页面发送 + curl 各一次，确认 DONE 收尾、无中断）
5. 管理面板：Plan Mode 进入/退出、权限模式切换、任务列表刷新、HITL 待确认审批（FR-7.3）
6. 会话续聊：同一用户/会话二次发送，验证上下文保留
7. 技能：对话触发 `prd-summarizer`，确认技能注册日志
8. M4 沙箱验收（有 Docker 的 Linux 环境）：按第六节 6.1~6.4 执行
