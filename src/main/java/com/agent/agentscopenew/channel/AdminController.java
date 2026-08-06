package com.agent.agentscopenew.channel;

import com.agent.agentscopenew.agent.AgentRegistry;
import com.agent.agentscopenew.security.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理 API 控制器。
 * <p>
 * 提供会话管理、任务列表、Plan Mode 操作、权限模式切换等后台管理能力。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AgentRegistry agentRegistry;

    public AdminController(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

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
     * 查询会话的任务列表。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/tasks")
    public Mono<Map<String, Object>> listTasks(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId) {
        // 任务列表通过 AgentState.tasksContext 获取，M3 实现完整逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("tenant", tenant);
        result.put("userId", userId);
        result.put("sessionId", sessionId);
        result.put("tasks", List.of());
        result.put("note", "任务列表将于 M3 里程碑实现完整功能");
        return Mono.just(result);
    }

    /**
     * 查询 Plan 状态。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/plan")
    public Mono<Map<String, Object>> getPlanStatus(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("tenant", tenant);
        result.put("userId", userId);
        result.put("sessionId", sessionId);
        result.put("planMode", false);
        result.put("note", "Plan Mode 状态查询将于 M3 里程碑实现完整功能");
        return Mono.just(result);
    }

    /**
     * 程序化进入 Plan Mode。
     */
    @PostMapping("/sessions/{tenant}/{userId}/{sessionId}:enter-plan-mode")
    public Mono<Map<String, String>> enterPlanMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId) {
        log.info("enter-plan-mode: tenant={}, user={}, session={}", tenant, userId, sessionId);
        return Mono.just(Map.of("status", "ok", "message", "Plan Mode 进入请求已发出（M3 实现完整逻辑）"));
    }

    /**
     * 程序化退出 Plan Mode（绕过 HITL，仅管理员）。
     */
    @PostMapping("/sessions/{tenant}/{userId}/{sessionId}:exit-plan-mode")
    public Mono<Map<String, String>> exitPlanMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId) {
        log.info("exit-plan-mode: tenant={}, user={}, session={}", tenant, userId, sessionId);
        return Mono.just(Map.of("status", "ok", "message", "Plan Mode 退出请求已发出（M3 实现完整逻辑）"));
    }

    /**
     * 查询权限模式。
     */
    @GetMapping("/sessions/{tenant}/{userId}/{sessionId}/permission-mode")
    public Mono<Map<String, Object>> getPermissionMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId) {
        return Mono.just(Map.of("mode", "DEFAULT", "tenant", tenant, "sessionId", sessionId));
    }

    /**
     * 设置权限模式。
     */
    @PutMapping("/sessions/{tenant}/{userId}/{sessionId}/permission-mode")
    public Mono<Map<String, String>> setPermissionMode(
            @PathVariable String tenant,
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String mode = body.getOrDefault("mode", "DEFAULT");
        log.info("set-permission-mode: tenant={}, user={}, session={}, mode={}",
                tenant, userId, sessionId, mode);
        return Mono.just(Map.of("status", "ok", "mode", mode));
    }
}