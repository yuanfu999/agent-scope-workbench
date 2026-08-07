package com.agent.agentscopenew.dto.response;

/**
 * Plan Mode 状态响应。
 *
 * @param userId          复合 userId（tenant__userId）
 * @param sessionId       复合 sessionId（agent__session）
 * @param planActive      是否处于 Plan Mode
 * @param currentPlanFile 当前计划文件路径
 */
public record PlanStatusResponse(String userId, String sessionId, boolean planActive,
                                 String currentPlanFile) {
}
