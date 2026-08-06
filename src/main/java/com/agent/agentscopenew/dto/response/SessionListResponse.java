package com.agent.agentscopenew.dto.response;

import java.util.Set;

/**
 * 会话列表响应。
 *
 * @param userId   复合 userId（tenant:userId）
 * @param sessions 会话 ID 集合
 * @param count    会话数量
 */
public record SessionListResponse(String userId, Set<String> sessions, int count) {
}
