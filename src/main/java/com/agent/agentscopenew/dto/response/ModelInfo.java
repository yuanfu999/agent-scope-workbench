package com.agent.agentscopenew.dto.response;

/**
 * 可选模型信息。
 *
 * @param id      模型 API 名称（如 deepseek-v4-pro）
 * @param label   页面展示名称（如 DeepSeek V4 Pro）
 * @param agentId 该模型对应的 Agent 名称（对话路由用）
 */
public record ModelInfo(String id, String label, String agentId) {
}
