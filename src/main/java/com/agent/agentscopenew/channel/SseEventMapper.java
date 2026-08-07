package com.agent.agentscopenew.channel;

import com.agent.agentscopenew.dto.event.ConfirmToolView;
import com.agent.agentscopenew.dto.event.SseEvent;
import com.agent.agentscopenew.dto.response.ErrorResponse;

import com.alibaba.fastjson2.JSON;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;

import lombok.extern.slf4j.Slf4j;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * SSE 事件映射器：将 AgentScope {@link AgentEvent} 转换为 SSE JSON 协议。
 * <p>
 * 输出格式（每个事件一个 JSON 对象，由 Spring WebFlux SSE 编码器统一添加
 * {@code data:} 前缀与换行分隔）：
 * <pre>
 * { "type": "TEXT_BLOCK_DELTA", "id": "evt-001", "delta": "你好" }
 * { "type": "TOOL_CALL_START",  "id": "evt-002", "toolCallName": "agent_spawn", "toolCallId": "call-1" }
 * { "type": "TOOL_RESULT_END",  "id": "evt-003", "toolCallName": "agent_spawn", "status": "SUCCESS" }
 * { "type": "SUBAGENT_EXPOSED", "id": "evt-004", "subagentId": "sa-xxx", "agentId": "researcher", "label": "调研员" }
 * { "type": "REQUIRE_CONFIRM",  "id": "evt-005", "replyId": "reply-1", "tools": [{"id":"call-1","name":"plan_exit","arguments":{}}] }
 * { "type": "AGENT_END",        "id": "evt-006", "finishReason": "END" }
 * </pre>
 */
@Slf4j
public final class SseEventMapper {

    private SseEventMapper() {
        // 工具类，禁止实例化
    }

    /**
     * 将 AgentEvent 转换为 SSE JSON 字符串。
     *
     * @param event   AgentScope 事件
     * @param eventId 事件序号（用于生成 id 字段）
     * @return SSE JSON 字符串
     */
    public static String toJson(AgentEvent event, long eventId) {
        String id = "evt-" + String.format("%03d", eventId);
        SseEvent sseEvent;
        if (event instanceof TextBlockDeltaEvent e) {
            sseEvent = SseEvent.builder().type("TEXT_BLOCK_DELTA").id(id).delta(e.getDelta()).build();
        } else if (event instanceof ToolCallStartEvent e) {
            sseEvent = SseEvent.builder().type("TOOL_CALL_START").id(id)
                    .toolCallName(e.getToolCallName()).toolCallId(e.getToolCallId()).build();
        } else if (event instanceof ToolResultEndEvent e) {
            sseEvent = SseEvent.builder().type("TOOL_RESULT_END").id(id)
                    .toolCallName(e.getToolCallName())
                    .status(e.getState() != null ? e.getState().name() : "SUCCESS").build();
        } else if (event instanceof SubagentExposedEvent e) {
            sseEvent = SseEvent.builder().type("SUBAGENT_EXPOSED").id(id)
                    .subagentId(e.getSubagentId()).agentId(e.getAgentId())
                    .label(e.getLabel() != null ? e.getLabel() : "").build();
        } else if (event instanceof RequireUserConfirmEvent e) {
            // FR-7.3：工具执行需用户批准，下发工具清单供前端渲染审批卡片
            List<ConfirmToolView> tools = e.getToolCalls().stream()
                    .map(tc -> new ConfirmToolView(tc.getId(), tc.getName(), tc.getInput()))
                    .toList();
            sseEvent = SseEvent.builder().type("REQUIRE_CONFIRM").id(id)
                    .replyId(e.getReplyId()).tools(tools).build();
        } else if (event instanceof AgentEndEvent e) {
            sseEvent = SseEvent.builder().type("AGENT_END").id(id)
                    .finishReason(e.getReplyId() != null ? e.getReplyId() : "END").build();
        } else {
            // 未知事件类型，原样透传 type 名（前向兼容）
            sseEvent = SseEvent.builder().type(event.getType().name()).id(id)
                    .rawType(event.getType().name()).build();
            log.trace("透传未知事件类型: {}", event.getType());
        }
        return JSON.toJSONString(sseEvent);
    }

    /**
     * 将 Agent 事件流映射为 SSE JSON 字符串流。
     * <p>
     * 统一处理事件序号、空事件过滤、DONE 结束标记与流内异常兜底
     * （避免 ERR_INCOMPLETE_CHUNKED_ENCODING）。
     *
     * @param eventFlux Agent 事件流
     * @return SSE JSON 字符串流
     */
    public static Flux<String> toStream(Flux<AgentEvent> eventFlux) {
        return eventFlux
                .index()
                .map(tuple -> {
                    long index = tuple.getT1();
                    AgentEvent event = tuple.getT2();
                    String json = toJson(event, index);
                    return json != null ? json : "";
                })
                .filter(s -> !s.isEmpty())
                .concatWithValues(doneEvent())
                .onErrorResume(e -> {
                    // 流内异常：下发 error 事件后优雅结束，避免 ERR_INCOMPLETE_CHUNKED_ENCODING
                    log.error("SSE 流错误: {}", e.getMessage(), e);
                    return Flux.just(errorEvent("流式对话中断: " + e.getMessage()));
                });
    }

    /**
     * 获取 SSE 结束标记事件（返回纯 JSON，由框架添加 data: 前缀）。
     */
    public static String doneEvent() {
        SseEvent done = SseEvent.builder().type("DONE").id("evt-done").build();
        return JSON.toJSONString(done);
    }

    /**
     * 获取 SSE 错误事件（返回纯 JSON，由框架添加 data: 前缀）。
     *
     * @param error 错误信息
     */
    public static String errorEvent(String error) {
        return JSON.toJSONString(new ErrorResponse(error));
    }
}
