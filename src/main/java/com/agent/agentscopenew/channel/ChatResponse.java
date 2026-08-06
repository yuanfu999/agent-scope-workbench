package com.agent.agentscopenew.channel;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.agentscope.core.message.ContentBlock;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 聊天响应 bean：成功时携带应答内容，失败时仅携带 error。
 * <p>
 * 序列化时仅输出非 null 字段。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    /** Agent 应答内容块列表（成功时）。 */
    private List<ContentBlock> response;

    /** 会话 ID（成功时）。 */
    private String sessionId;

    /** 应答 Agent 名称（成功时）。 */
    private String agentId;

    /** 错误信息（失败时）。 */
    private String error;
}
