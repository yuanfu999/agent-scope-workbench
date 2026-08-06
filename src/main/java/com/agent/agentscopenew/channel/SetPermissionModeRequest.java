package com.agent.agentscopenew.channel;

/**
 * 设置权限模式请求体。
 *
 * @param mode 权限模式字符串（DEFAULT/DONT_ASK/BYPASS，缺省回退 DEFAULT）
 */
public record SetPermissionModeRequest(String mode) {
}
