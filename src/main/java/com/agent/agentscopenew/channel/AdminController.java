package com.agent.agentscopenew.channel;

import com.agent.agentscopenew.agent.AgentRegistry;
import com.agent.agentscopenew.config.WorkbenchProperties;

import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.Task;
import io.agentscope.harness.agent.HarnessAgent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
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
 * 一致的复合键格式（{@code tenant:userId} / {@code agentId:sessionId}）。
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

    /**
     * 查询 Agent 注册表。
     */
    @GetMapping("/agents")
    public Mono<Map<String, Object>> listAgents() {
        Map<String, Object> result = new HashMap<>();
        result.put("mainAgent", agentRegistry.getMainAgentName());
        result.put("agents", agentRegistry.getAllAgents().keySet());
        result.put("count", agentRegistry.agentCount());
        return Mono.just(result);
    }

    /**
     * 列出某用户在指定 Agent 下的全部会话。
     */
    @GetMapping("/sessions/{tenant}/{userId}")
    public Mono<Map<String, Object>> listSessions(
            @PathVariable String tenant,
            @PathVariable String userId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        Set<String> sessionIds = agent.getStateStore().listSessionIds(compositeUserId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", compositeUserId);
        result.put("sessions", sessionIds);
        result.put("count", sessionIds.size());
        return Mono.just(result);
    }

    /**
     * 查询会话的任务列表（来自 AgentState.tasksContext）。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/tasks")
    public Mono<Map<String, Object>> listTasks(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", compositeUserId);
        result.put("sessionId", compositeSessionId);

        Optional<AgentState> state = loadAgentState(agent, compositeUserId, compositeSessionId);
        if (state.isPresent() && state.get().getTasksContext() != null) {
            List<Task> tasks = state.get().getTasksContext().getTasks();
            result.put("tasks", toTaskMaps(tasks));
            result.put("count", tasks.size());
        } else {
            result.put("tasks", List.of());
            result.put("count", 0);
        }
        return Mono.just(result);
    }

    /**
     * 查询 Plan 状态。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/plan")
    public Mono<Map<String, Object>> getPlanStatus(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", compositeUserId);
        result.put("sessionId", compositeSessionId);
        result.put("planActive", agent.isPlanModeActive(compositeUserId, compositeSessionId));

        Optional<AgentState> state = loadAgentState(agent, compositeUserId, compositeSessionId);
        if (state.isPresent() && state.get().getPlanModeContext() != null) {
            result.put("currentPlanFile", state.get().getPlanModeContext().getCurrentPlanFile());
        } else {
            result.put("currentPlanFile", "");
        }
        return Mono.just(result);
    }

    /**
     * 程序化进入 Plan Mode。
     */
    @PostMapping("/sessions/{tenant}/{userId}/{sessionId}:enter-plan-mode")
    public Mono<Map<String, String>> enterPlanMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        agent.enterPlanMode(compositeUserId, compositeSessionId);
        log.info("enter-plan-mode: userId={}, sessionId={}", compositeUserId, compositeSessionId);
        return Mono.just(Map.of("status", "ok", "message", "已进入 Plan Mode"));
    }

    /**
     * 程序化退出 Plan Mode（绕过 HITL，仅管理员）。
     */
    @PostMapping("/sessions/{tenant}/{userId}/{sessionId}:exit-plan-mode")
    public Mono<Map<String, String>> exitPlanMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        agent.exitPlanMode(compositeUserId, compositeSessionId);
        log.info("exit-plan-mode: userId={}, sessionId={}", compositeUserId, compositeSessionId);
        return Mono.just(Map.of("status", "ok", "message", "已退出 Plan Mode"));
    }

    /**
     * 查询权限模式。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/permission-mode")
    public Mono<Map<String, Object>> getPermissionMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId) {
        HarnessAgent agent = resolveAgent(agentId);
        String compositeUserId = compositeUserId(tenant, userId);
        String compositeSessionId = compositeSessionId(agent, sessionId);

        PermissionMode mode = agent.getPermissionMode(compositeUserId, compositeSessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", compositeUserId);
        result.put("sessionId", compositeSessionId);
        result.put("mode", mode != null ? mode.getValue() : PermissionMode.DEFAULT.getValue());
        return Mono.just(result);
    }

    /**
     * 设置权限模式。
     * <p>
     * 安全约束：{@code BYPASS} 仅允许在沙箱文件系统模式下使用。
     */
    @PutMapping("/sessions/{tenant}/{userId}/{sessionId}/permission-mode")
    public Mono<Map<String, String>> setPermissionMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId,
            @RequestBody String bodyJson) {
        HarnessAgent agent = resolveAgent(agentId);
        // 使用 fastjson 反序列化请求体，非法 JSON 返回 400
        JSONObject body;
        try {
            body = JSON.parseObject(bodyJson);
        } catch (JSONException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不是合法 JSON");
        }
        String modeStr = (body != null) ? body.getString("mode") : null;
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
        return Mono.just(Map.of("status", "ok", "mode", mode.getValue()));
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
        return tenant + ":" + userId;
    }

    /**
     * 组装复合 sessionId（存储键第二分量）。
     */
    private String compositeSessionId(HarnessAgent agent, String sessionId) {
        return agent.getName() + ":" + sessionId;
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
     * 将任务列表映射为响应 JSON。
     */
    private List<Map<String, Object>> toTaskMaps(List<Task> tasks) {
        List<Map<String, Object>> list = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", task.getId());
            map.put("subject", task.getSubject());
            map.put("description", task.getDescription());
            map.put("state", task.getState() != null ? task.getState().name() : null);
            map.put("owner", task.getOwner());
            map.put("blocks", task.getBlocks());
            map.put("blockedBy", task.getBlockedBy());
            map.put("createdAt", task.getCreatedAt());
            list.add(map);
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
