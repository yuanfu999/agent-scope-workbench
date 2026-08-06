package com.agent.agentscopenew.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.SubagentExposedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSE 事件映射器：将 AgentScope {@link AgentEvent} 转换为 SSE JSON 协议。
 * <p>
 * 输出格式（每行一个 JSON 对象）：
 * <pre>
 * { "type": "TEXT_BLOCK_DELTA", "id": "evt-001", "delta": "你好" }
 * { "type": "TOOL_CALL_START",  "id": "evt-002", "toolCallName": "agent_spawn", "toolCallId": "call-1" }
 * { "type": "TOOL_RESULT_END",  "id": "evt-003", "toolCallName": "agent_spawn", "status": "SUCCESS" }
 * { "type": "SUBAGENT_EXPOSED", "id": "evt-004", "subagentId": "sa-xxx", "agentId": "researcher", "label": "调研员" }
 * { "type": "AGENT_END",        "id": "evt-005", "finishReason": "END" }
 * </pre>
 */
public final class SseEventMapper {

    private static final Logger log = LoggerFactory.getLogger(SseEventMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseEventMapper() {
        // 工具类，禁止实例化
    }

    /**
     * 将 AgentEvent 转换为 SSE JSON 字符串。
     *
     * @param event  AgentScope 事件
     * @param eventId 事件序号（用于生成 id 字段）
     * @return SSE JSON 字符串，或 null（如果事件类型不在白名单中）
     */
    public static String toJson(AgentEvent event, long eventId) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "evt-" + String.format("%03d", eventId));

        if (event instanceof TextBlockDeltaEvent e) {
            root.put("type", "TEXT_BLOCK_DELTA");
            root.put("delta", e.getDelta());
        } else if (event instanceof ToolCallStartEvent e) {
            root.put("type", "TOOL_CALL_START");
            root.put("toolCallName", e.getToolCallName());
            root.put("toolCallId", e.getToolCallId());
        } else if (event instanceof ToolResultEndEvent e) {
            root.put("type", "TOOL_RESULT_END");
            root.put("toolCallName", e.getToolCallName());
            root.put("status", e.getState() != null ? e.getState().name() : "SUCCESS");
        } else if (event instanceof SubagentExposedEvent e) {
            root.put("type", "SUBAGENT_EXPOSED");
            root.put("subagentId", e.getSubagentId());
            root.put("agentId", e.getAgentId());
            root.put("label", e.getLabel() != null ? e.getLabel() : "");
        } else if (event instanceof AgentEndEvent e) {
            root.put("type", "AGENT_END");
            root.put("finishReason", e.getReplyId() != null ? e.getReplyId() : "END");
        } else {
            // 未知事件类型，原样透传 type 名（前向兼容）
            root.put("type", event.getType().name());
            root.put("rawType", event.getType().name());
            log.trace("透传未知事件类型: {}", event.getType());
        }

        return root.toString();
    }

    /**
     * 获取 SSE 结束标记事件。
     */
    public static String doneEvent() {
        return "event: done\ndata: {\"type\":\"DONE\",\"id\":\"evt-done\"}\n\n";
    }
}