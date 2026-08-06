package com.agent.agentscopenew.dto.response;

import java.util.Set;

/**
 * Agent 注册表响应。
 *
 * @param mainAgent 主 Agent 名称
 * @param agents    已注册 Agent 名称集合
 * @param count     Agent 数量
 */
public record AgentListResponse(String mainAgent, Set<String> agents, int count) {
}
