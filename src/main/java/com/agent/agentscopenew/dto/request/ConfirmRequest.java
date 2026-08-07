package com.agent.agentscopenew.dto.request;

/**
 * 工具调用审批请求体（FR-7.3 Plan 批准/拒绝 HITL）。
 *
 * @param confirmed true=批准执行工具，false=拒绝并继续规划
 */
public record ConfirmRequest(boolean confirmed) {
}
