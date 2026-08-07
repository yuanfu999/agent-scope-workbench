package com.agent.agentscopenew.dto.response;

/**
 * 权限模式查询响应。
 *
 * @param userId    复合 userId（tenant__userId）
 * @param sessionId 复合 sessionId（agent__session）
 * @param mode      权限模式（小写字符串：default/dont_ask/bypass）
 */
public record PermissionModeResponse(String userId, String sessionId, String mode) {
}
