package com.agent.agentscopenew.security;

/**
 * 租户上下文：将请求头中的租户/用户信息装配为 AgentScope RuntimeContext 兼容格式。
 * <p>
 * 根据官方文档推荐，userId 采用 {@code tenantId:userId} 复合格式，
 * sessionId 采用 {@code agentId:sessionId} 复合格式，确保存储键天然隔离。
 *
 * @param tenantId  平台租户 ID
 * @param rawUserId 原始用户 ID
 * @param sessionId 会话 ID（含 agentId 前缀）
 * @param agentId   目标 Agent ID
 */
public record TenantContext(
        String tenantId,
        String rawUserId,
        String sessionId,
        String agentId) {

    /**
     * 获取复合 userId（存储键的第一分量）。
     */
    public String userId() {
        return tenantId + ":" + rawUserId;
    }

    /**
     * 从请求头创建 TenantContext。
     */
    public static TenantContext fromHeaders(String xTenantId, String xUserId, String sessionId, String agentId) {
        String tenant = (xTenantId != null && !xTenantId.isBlank()) ? xTenantId : "default";
        String user = (xUserId != null && !xUserId.isBlank()) ? xUserId : "anonymous";
        return new TenantContext(tenant, user, sessionId, agentId);
    }
}