package com.agent.agentscopenew.dto.response;

import java.util.List;

/**
 * 任务视图 bean：Task 的响应封装。
 *
 * @param id          任务 ID
 * @param subject     任务主题
 * @param description 任务描述
 * @param state       任务状态（枚举名）
 * @param owner       负责人
 * @param blocks      阻塞的任务 ID 列表
 * @param blockedBy   被哪些任务阻塞
 * @param createdAt   创建时间
 */
public record TaskView(String id, String subject, String description, String state,
                       String owner, List<String> blocks, List<String> blockedBy,
                       String createdAt) {
}
