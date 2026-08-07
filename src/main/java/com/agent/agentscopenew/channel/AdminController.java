package com.agent.agentscopenew.channel;

import com.agent.agentscopenew.agent.AgentRegistry;
import com.agent.agentscopenew.config.WorkbenchProperties;
import com.agent.agentscopenew.dto.event.ConfirmToolView;
import com.agent.agentscopenew.dto.request.ConfirmRequest;
import com.agent.agentscopenew.dto.request.SetPermissionModeRequest;
import com.agent.agentscopenew.dto.response.AgentListResponse;
import com.agent.agentscopenew.dto.response.ModelInfo;
import com.agent.agentscopenew.dto.response.ModelListResponse;
import com.agent.agentscopenew.dto.response.OperationResponse;
import com.agent.agentscopenew.dto.response.PendingConfirmResponse;
import com.agent.agentscopenew.dto.response.PermissionModeResponse;
import com.agent.agentscopenew.dto.response.PermissionModeResult;
import com.agent.agentscopenew.dto.response.PlanStatusResponse;
import com.agent.agentscopenew.dto.response.SessionListResponse;
import com.agent.agentscopenew.dto.response.TaskListResponse;
import com.agent.agentscopenew.dto.response.TaskView;
import com.agent.agentscopenew.security.TenantContext;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.Task;
import io.agentscope.harness.agent.HarnessAgent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 管理 API 控制器。
 * <p>
 * 提供会话管理、任务列表、Plan Mode 操作、权限模式切换等后台管理能力。
 * 状态读写直接走 {@link HarnessAgent} 的官方管理 API 与共享
 * {@link AgentStateStore}，userId / sessionId 采用与 ChatController
 * 一致的复合键格式（{@code tenant__userId} / {@code agentId__sessionId}），
 * 分隔符见 {@link TenantContext#KEY_SEPARATOR}。
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    /** AgentState 在 stateStore 中的保存键（官方常量）。 */
    private static final String STATE_KEY = "agent_state";

    private final AgentRegistry agentRegistry;
    private final WorkbenchProperties workbenchProperties;
    private final PendingConfirmRegistry pendingConfirmRegistry;

    /**
     * 查询 Agent 注册表。
     */
    @GetMapping("/agents")
    public Mono<AgentListResponse> listAgents() {
        return Mono.just(new AgentListResponse(
                agentRegistry.getMainAgentName(),
                agentRegistry.getAllAgents().keySet(),
                agentRegistry.agentCount()));
    }

    /**
     * 查询可选模型列表（供前端模型选择器使用）。
     * <p>
     * 数据源为 workbench.agents 配置，每个 Agent 对应一个可选模型，
     * 页面按 agentId 路由对话到对应模型。
     */
    @GetMapping("/models")
    public Mono<ModelListResponse> listModels() {
        List<ModelInfo> models = workbenchProperties.listModels();
        return Mono.just(new ModelListResponse(models, models.size()));
    }

    /**
     * 列出某用户在指定 Agent 下的全部会话。
     */
    @GetMapping("/sessions/{tenant}/{userId}")
    public Mono<SessionListResponse> listSessions(
            @PathVariable String tenant,
            @PathVariable String userId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        Set<String> sessionIds = agent.getStateStore().listSessionIds(compositeUserId);
        return Mono.just(new SessionListResponse(compositeUserId, sessionIds, sessionIds.size()));
    }

    /**
     * 查询会话的任务列表（来自 AgentState.tasksContext）。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/tasks")
    public Mono<TaskListResponse> listTasks(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        Optional<AgentState> state = loadAgentState(agent, compositeUserId, compositeSessionId);
        if (state.isPresent() && state.get().getTasksContext() != null) {
            List<Task> tasks = state.get().getTasksContext().getTasks();
            return Mono.just(new TaskListResponse(
                    compositeUserId, compositeSessionId, toTaskViews(tasks), tasks.size()));
        }
        return Mono.just(new TaskListResponse(compositeUserId, compositeSessionId, List.of(), 0));
    }

    /**
     * 查询 Plan 状态。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/plan")
    public Mono<PlanStatusResponse> getPlanStatus(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        Optional<AgentState> state = loadAgentState(agent, compositeUserId, compositeSessionId);
        String currentPlanFile = "";
        if (state.isPresent() && state.get().getPlanModeContext() != null
                && state.get().getPlanModeContext().getCurrentPlanFile() != null) {
            currentPlanFile = state.get().getPlanModeContext().getCurrentPlanFile();
        }
        return Mono.just(new PlanStatusResponse(
                compositeUserId, compositeSessionId,
                agent.isPlanModeActive(compositeUserId, compositeSessionId), currentPlanFile));
    }

    /**
     * 程序化进入 Plan Mode。
     */
    @PostMapping("/sessions/{tenant}/{userId}/{sessionId}:enter-plan-mode")
    public Mono<OperationResponse> enterPlanMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        agent.enterPlanMode(compositeUserId, compositeSessionId);
        log.info("enter-plan-mode: userId={}, sessionId={}", compositeUserId, compositeSessionId);
        return Mono.just(new OperationResponse("ok", "已进入 Plan Mode"));
    }

    /**
     * 程序化退出 Plan Mode（绕过 HITL，仅管理员）。
     */
    @PostMapping("/sessions/{tenant}/{userId}/{sessionId}:exit-plan-mode")
    public Mono<OperationResponse> exitPlanMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        agent.exitPlanMode(compositeUserId, compositeSessionId);
        log.info("exit-plan-mode: userId={}, sessionId={}", compositeUserId, compositeSessionId);
        return Mono.just(new OperationResponse("ok", "已退出 Plan Mode"));
    }

    /**
     * 查询权限模式。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/permission-mode")
    public Mono<PermissionModeResponse> getPermissionMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        PermissionMode mode = agent.getPermissionMode(compositeUserId, compositeSessionId);
        String modeValue = mode != null ? mode.getValue() : PermissionMode.DEFAULT.getValue();
        return Mono.just(new PermissionModeResponse(compositeUserId, compositeSessionId, modeValue));
    }

    /**
     * 设置权限模式。
     * <p>
     * 安全约束：{@code BYPASS} 仅允许在沙箱文件系统模式下使用。
     */
    @PutMapping("/sessions/{tenant}/{userId}/{sessionId}/permission-mode")
    public Mono<PermissionModeResult> setPermissionMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId,
            @RequestBody(required = false) SetPermissionModeRequest request) {
        HarnessAgent agent = resolveAgent(agentId);
        String modeStr = (request != null) ? request.mode() : null;
        PermissionMode mode = parsePermissionMode(modeStr);

        if (mode == PermissionMode.BYPASS && !isSandboxAgent(agentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "BYPASS 权限模式仅允许在沙箱文件系统下使用");
        }

        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);
        agent.setPermissionMode(compositeUserId, compositeSessionId, mode);
        log.info("set-permission-mode: userId={}, sessionId={}, mode={}",
                compositeUserId, compositeSessionId, mode.getValue());
        return Mono.just(new PermissionModeResult("ok", mode.getValue()));
    }

    /**
     * 查询待确认的工具调用（FR-7.3 Plan 批准/拒绝 HITL）。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/pending-confirm")
    public Mono<PendingConfirmResponse> getPendingConfirm(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        Optional<PendingConfirmRegistry.PendingConfirm> record =
                pendingConfirmRegistry.lookup(compositeUserId, compositeSessionId);
        if (record.isEmpty()) {
            return Mono.just(new PendingConfirmResponse(false, "", List.of(), null));
        }
        PendingConfirmRegistry.PendingConfirm pc = record.get();
        List<ConfirmToolView> tools = pc.toolCalls().stream()
                .map(tc -> new ConfirmToolView(tc.getId(), tc.getName(), tc.getInput()))
                .toList();
        return Mono.just(new PendingConfirmResponse(true, pc.replyId(), tools, pc.createdAt()));
    }

    /**
     * 提交工具调用审批结果（FR-7.3）。
     * <p>
     * 批准：以 ALLOWED 状态恢复工具执行并进入 BUILD 模式；拒绝：以 DENIED
     * 结果回灌，Agent 留在 PLAN 模式继续修订。响应为 SSE 事件流，前端
     * 复用对话渲染逻辑继续展示后续输出。
     */
    @PostMapping(value = "/sessions/{tenant}/{userId}/{sessionId}:confirm",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> confirmToolCall(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId,
            @RequestBody(required = false) ConfirmRequest request) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        Optional<PendingConfirmRegistry.PendingConfirm> record =
                pendingConfirmRegistry.lookup(compositeUserId, compositeSessionId);
        if (record.isEmpty()) {
            return Flux.just(SseEventMapper.errorEvent("没有待确认的工具调用"));
        }
        pendingConfirmRegistry.remove(compositeUserId, compositeSessionId);
        boolean confirmed = request != null && request.confirmed();

        List<ConfirmResult> results = record.get().toolCalls().stream()
                .map(tc -> new ConfirmResult(confirmed, tc))
                .toList();
        Msg confirmMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(confirmed ? "用户批准了以上工具调用，继续执行。" : "用户拒绝了以上工具调用，请调整方案。")
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .sessionId(compositeSessionId)
                .userId(compositeUserId)
                .build();

        log.info("confirm: user={}, session={}, confirmed={}, tools={}",
                compositeUserId, compositeSessionId, confirmed, results.size());
        return SseEventMapper.toStream(agent.streamEvents(List.of(confirmMsg), runtimeContext));
    }

    /**
     * 按名称解析 Agent，不存在时返回 404。
     *
     * @param agentId Agent 名称，为空时使用主 Agent
     * @return HarnessAgent 实例
     */
    private HarnessAgent resolveAgent(String agentId) {
        String name = (agentId == null || agentId.isBlank())
                ? agentRegistry.getMainAgentName() : agentId;
        HarnessAgent agent = agentRegistry.getAgent(name);
        if (agent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在: " + name);
        }
        return agent;
    }

    /**
     * 组装复合 userId（存储键第一分量）。
     */
    private String compositeUserId(String tenant, String userId) {
        return tenant + TenantContext.KEY_SEPARATOR + userId;
    }

    /**
     * 组装复合 sessionId（存储键第二分量）。
     */
    private String compositeSessionId(HarnessAgent agent, String sessionId) {
        return agent.getName() + TenantContext.KEY_SEPARATOR + sessionId;
    }

    /**
     * 从共享 stateStore 加载 AgentState。
     */
    private Optional<AgentState> loadAgentState(HarnessAgent agent, String userId, String sessionId) {
        AgentStateStore store = agent.getStateStore();
        if (store == null) {
            return Optional.empty();
        }
        return store.get(userId, sessionId, STATE_KEY, AgentState.class);
    }

    /**
     * 将任务列表转换为任务视图列表。
     */
    private List<TaskView> toTaskViews(List<Task> tasks) {
        List<TaskView> list = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            list.add(new TaskView(
                    task.getId(),
                    task.getSubject(),
                    task.getDescription(),
                    task.getState() != null ? task.getState().name() : null,
                    task.getOwner(),
                    task.getBlocks(),
                    task.getBlockedBy(),
                    task.getCreatedAt()));
        }
        return list;
    }

    /**
     * 解析权限模式字符串，非法值返回 400。
     */
    private PermissionMode parsePermissionMode(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) {
            return PermissionMode.DEFAULT;
        }
        String upper = modeStr.trim().toUpperCase();
        for (PermissionMode mode : PermissionMode.values()) {
            if (mode.name().equals(upper)) {
                return mode;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知权限模式: " + modeStr);
    }

    /**
     * 判断目标 Agent 是否运行在沙箱文件系统模式下（BYPASS 权限模式的前置条件）。
     */
    private boolean isSandboxAgent(String agentId) {
        if (workbenchProperties == null) {
            return false;
        }
        String name = (agentId == null || agentId.isBlank())
                ? agentRegistry.getMainAgentName() : agentId;
        return workbenchProperties.findAgent(name).filesystem().isSandbox();
    }
}
