package com.agent.agentscopenew.channel;

import com.agent.agentscopenew.agent.AgentRegistry;
import com.agent.agentscopenew.dto.request.ChatRequest;
import com.agent.agentscopenew.dto.response.ChatResponse;
import com.agent.agentscopenew.security.TenantContext;

import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 聊天控制器：REST 非流式 + SSE 流式对话接口。
 * <p>
 * 提供两套对话 API：
 * <ul>
 *   <li>{@code POST /api/v1/chat} — 非流式应答，返回完整 JSON 响应</li>
 *   <li>{@code GET /api/v1/chat/stream} — SSE 流式事件，实时推送推理过程</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final AgentRegistry agentRegistry;

    /**
     * 非流式对话接口。
     *
     * @param xTenantId  租户 ID（请求头）
     * @param xUserId    用户 ID（请求头）
     * @param request    请求体：{ "message": "...", "sessionId": "...", "agentId": "..." }
     * @return 聊天响应 bean（成功携带 response，失败携带 error）
     */
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatResponse> chat(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String xTenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String xUserId,
            @RequestBody ChatRequest request) {

        String message = request.message();
        if (message == null || message.isBlank()) {
            return Mono.just(ChatResponse.builder().error("message 不能为空").build());
        }

        String agentId = request.agentId() != null ? request.agentId() : agentRegistry.getMainAgentName();
        String sessionId = resolveSessionId(request.sessionId(), agentId, xTenantId, xUserId);

        TenantContext ctx = new TenantContext(xTenantId, xUserId, sessionId, agentId);
        ChatUiChannel channel = agentRegistry.getChatUiChannel();

        SendOptions options = new SendOptions(ctx.userId(), ctx.sessionId(), agentId);

        log.info("chat: tenant={}, user={}, session={}, agent={}, msgLen={}",
                xTenantId, xUserId, sessionId, agentId, message.length());

        return channel.send(options, message)
                .map(response -> ChatResponse.builder()
                        .response(response.getContent())
                        .sessionId(sessionId)
                        .agentId(agentId)
                        .build())
                .timeout(Duration.ofSeconds(300));
    }

    /**
     * SSE 流式对话接口。
     *
     * @param xTenantId  租户 ID（请求头）
     * @param xUserId    用户 ID（请求头）
     * @param message    用户消息
     * @param sessionId  会话 ID（可选，缺省自动生成）
     * @param agentId    Agent ID（可选，缺省使用 mainAgent）
     * @param subagentId 子 Agent ID（可选，直连子 Agent）
     * @return SSE 事件流
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String xTenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String xUserId,
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "agentId", required = false) String agentId,
            @RequestParam(value = "subagentId", required = false) String subagentId) {

        if (message == null || message.isBlank()) {
            return Flux.just(SseEventMapper.errorEvent("message 不能为空"));
        }

        String resolvedAgentId = agentId != null ? agentId : agentRegistry.getMainAgentName();
        String resolvedSessionId = resolveSessionId(sessionId, resolvedAgentId, xTenantId, xUserId);

        TenantContext ctx = new TenantContext(xTenantId, xUserId, resolvedSessionId, resolvedAgentId);
        ChatUiChannel channel = agentRegistry.getChatUiChannel();

        log.info("chat/stream: tenant={}, user={}, session={}, agent={}, subagent={}, msgLen={}",
                xTenantId, xUserId, resolvedSessionId, resolvedAgentId, subagentId, message.length());

        Flux<io.agentscope.core.event.AgentEvent> eventFlux;

        if (subagentId != null && !subagentId.isBlank()) {
            // 直连子 Agent（官方 API 以 subagentId 标识目标，无需 SendOptions）
            eventFlux = channel.sendToSubagentStream(subagentId, message);
        } else {
            SendOptions options = new SendOptions(ctx.userId(), ctx.sessionId(), resolvedAgentId);
            eventFlux = channel.sendStream(options, message);
        }

        return eventFlux
                .index()
                .map(tuple -> {
                    long index = tuple.getT1();
                    io.agentscope.core.event.AgentEvent event = tuple.getT2();
                    String json = SseEventMapper.toJson(event, index);
                    if (json == null) {
                        return "";
                    }
                    // 只输出纯 JSON，data: 前缀与换行由 SSE 编码器统一添加
                    return json;
                })
                .filter(s -> !s.isEmpty())
                .concatWithValues(SseEventMapper.doneEvent())
                .doOnError(e -> log.error("SSE 流错误", e));
    }

    /**
     * 解析会话 ID：如果未提供则按 userId 自动生成稳定 session。
     * <p>
     * 复合键分隔符统一使用 {@link TenantContext#KEY_SEPARATOR}，
     * 保证 userId / sessionId 均可安全用作 Windows 文件系统路径。
     */
    private String resolveSessionId(String rawSessionId, String agentId, String tenantId, String userId) {
        String separator = TenantContext.KEY_SEPARATOR;
        if (rawSessionId != null && !rawSessionId.isBlank()) {
            return agentId + separator + rawSessionId;
        }
        // 按 userId 生成稳定 session（同一用户的会话可恢复）
        return agentId + separator + tenantId + separator + userId;
    }
}