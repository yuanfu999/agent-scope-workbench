package com.agent.agentscopenew.dto.event;

import java.util.Map;

/**
 * 待确认工具调用视图（REQUIRE_CONFIRM 事件负载）。
 *
 * @param id        工具调用 ID（与 ToolUseBlock.id 一致）
 * @param name      工具名
 * @param arguments 工具入参（原始 Map，JSON 序列化输出）
 */
public record ConfirmToolView(String id, String name, Map<String, Object> arguments) {
}
