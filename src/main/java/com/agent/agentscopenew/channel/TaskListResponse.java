package com.agent.agentscopenew.channel;

import java.util.List;

/**
 * 任务列表响应。
 *
 * @param userId    复合 userId（tenant:userId）
 * @param sessionId 复合 sessionId（agent:session）
 * @param tasks     任务视图列表
 * @param count     任务数量
 */
public record TaskListResponse(String userId, String sessionId, List<TaskView> tasks, int count) {
}
