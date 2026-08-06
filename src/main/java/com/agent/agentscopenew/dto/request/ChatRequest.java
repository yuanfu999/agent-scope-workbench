package com.agent.agentscopenew.dto.request;

/**
 * 聊天请求体。
 *
 * @param message   用户消息
 * @param sessionId 会话 ID（可选，缺省按 userId 自动生成稳定会话）
 * @param agentId   Agent ID（可选，缺省使用主 Agent）
 */
public record ChatRequest(String message, String sessionId, String agentId) {
}
