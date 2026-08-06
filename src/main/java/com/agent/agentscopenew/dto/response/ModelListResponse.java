package com.agent.agentscopenew.dto.response;

import java.util.List;

/**
 * 可选模型列表响应。
 *
 * @param models 模型列表（配置顺序）
 * @param count  模型数量
 */
public record ModelListResponse(List<ModelInfo> models, int count) {
}
