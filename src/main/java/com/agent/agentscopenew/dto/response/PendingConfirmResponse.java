package com.agent.agentscopenew.dto.response;

import com.agent.agentscopenew.dto.event.ConfirmToolView;

import java.time.Instant;
import java.util.List;

/**
 * 待确认请求查询结果（FR-7.3）。
 *
 * @param pending   是否存在待确认请求
 * @param replyId   待确认回复 ID（无待确认时为空串）
 * @param tools     待确认工具调用列表
 * @param createdAt 登记时间（无待确认时为 null）
 */
public record PendingConfirmResponse(
        boolean pending, String replyId, List<ConfirmToolView> tools, Instant createdAt) {
}
