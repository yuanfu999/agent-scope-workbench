package com.agent.agentscopenew.channel;

/**
 * 管理操作结果响应。
 *
 * @param status  操作状态（ok）
 * @param message 操作提示信息
 */
public record OperationResponse(String status, String message) {
}
