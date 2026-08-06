package com.agent.agentscopenew.channel;

import lombok.Builder;
import lombok.Data;

/**
 * SSE 事件 bean：按事件类型携带对应业务字段。
 * <p>
 * 序列化时仅输出非 null 字段，字段名即 SSE JSON 协议字段名。
 */
@Data
@Builder
public class SseEvent {

    /** 事件类型（TEXT_BLOCK_DELTA/TOOL_CALL_START/AGENT_END/DONE 等）。 */
    private String type;

    /** 事件序号（evt-001 格式）。 */
    private String id;

    /** 文本增量（TEXT_BLOCK_DELTA）。 */
    private String delta;

    /** 工具名（TOOL_CALL_START/TOOL_RESULT_END）。 */
    private String toolCallName;

    /** 工具调用 ID（TOOL_CALL_START）。 */
    private String toolCallId;

    /** 工具执行状态（TOOL_RESULT_END）。 */
    private String status;

    /** 子 Agent ID（SUBAGENT_EXPOSED）。 */
    private String subagentId;

    /** 子 Agent 所属 Agent（SUBAGENT_EXPOSED）。 */
    private String agentId;

    /** 子 Agent 展示名（SUBAGENT_EXPOSED）。 */
    private String label;

    /** 结束原因（AGENT_END）。 */
    private String finishReason;

    /** 未知事件原始类型（前向兼容透传）。 */
    private String rawType;
}
