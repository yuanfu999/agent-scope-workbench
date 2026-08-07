# AgentScope Workbench 阶段测试指南

> 适用版本：当前 main 分支（AgentScope 2.0.0 / Spring Boot 4.1）
> 面向对象：测试工程师
> 文档日期：2026-08-07
> 阶段状态：**M1 骨架阶段已验收通过**（2026-08-07，非流式对话/管理面板/模型选择等全部通过；SSE 流式对话除外，见第六节已知问题）

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

## 六、已知问题（务必知悉）

- **SSE 流中断（未修复）**：`GET /api/v1/chat/stream` 存在流中断问题——浏览器报 `ERR_INCOMPLETE_CHUNKED_ENCODING`，curl 复现仅收到 68 字节（`AGENT_START` 事件，36ms）即断开。服务端在模型调用阶段异常关闭连接，`doOnError` 只打日志未向客户端下发错误事件。已确认 Agent 初始化、Skill 加载、DeepSeek API 调用均正常（请求真实发出），问题定位在流事件回传链路。**流式对话（含页面发送）暂不适合作为通过项验收**，非流式 `/chat` 接口可正常测试。
- 子 Agent 需对话中由主 Agent 触发暴露后才出现，属动态能力，非固定入口。

## 七、建议测试顺序

1. 启动服务（确认无 `DEEPSEEK_API_KEY 未配置` 报错）
2. `GET /api/v1/admin/agents`、`/models`、页面顶栏下拉加载
3. 非流式 `/chat` 全链路（双 Agent 各发一条）
4. 管理面板：Plan Mode 进入/退出、权限模式切换、任务列表刷新
5. 会话续聊：同一用户/会话二次发送，验证上下文保留
6. 流式对话：按已知问题标注执行，记录实际表现即可
