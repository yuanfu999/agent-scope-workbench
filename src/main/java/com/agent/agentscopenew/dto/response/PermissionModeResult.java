package com.agent.agentscopenew.dto.response;

/**
 * 权限模式设置结果响应。
 *
 * @param status 操作状态（ok）
 * @param mode   生效的权限模式（小写字符串）
 */
public record PermissionModeResult(String status, String mode) {
}
