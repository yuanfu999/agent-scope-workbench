package com.agent.agentscopenew.channel;

import io.agentscope.core.message.ToolUseBlock;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待用户确认请求注册表（FR-7.3 Plan 批准/拒绝 HITL）。
 * <p>
 * SSE 流中拦截 {@code RequireUserConfirmEvent} 后登记，管理端批准/拒绝时
 * 取出原始 {@link ToolUseBlock} 构造 {@code ConfirmResult} 回灌 Agent 恢复执行。
 * 键为 {@code compositeUserId + "::" + compositeSessionId}，与
 * {@link AdminController} / {@link ChatController} 的复合键约定一致。
 */
@Component
public class PendingConfirmRegistry {

    /** 复合键分隔符（userId 与 sessionId 两分量之间）。 */
    public static final String KEY_SEPARATOR = "::";

    private final Map<String, PendingConfirm> entries = new ConcurrentHashMap<>();

    /**
     * 待确认记录。
     *
     * @param replyId   待确认回复 ID（RequireUserConfirmEvent.replyId）
     * @param toolCalls 待确认的工具调用（原样保留，供 ConfirmResult 复用）
     * @param createdAt 登记时间
     */
    public record PendingConfirm(String replyId, List<ToolUseBlock> toolCalls, Instant createdAt) {
    }

    /**
     * 登记一条待确认请求（同键重复时覆盖旧记录）。
     *
     * @param userId    复合 userId
     * @param sessionId 复合 sessionId
     * @param replyId   待确认回复 ID
     * @param toolCalls 待确认的工具调用列表
     */
    public void register(String userId, String sessionId, String replyId, List<ToolUseBlock> toolCalls) {
        entries.put(key(userId, sessionId), new PendingConfirm(replyId, toolCalls, Instant.now()));
    }

    /**
     * 查询待确认请求。
     *
     * @param userId    复合 userId
     * @param sessionId 复合 sessionId
     * @return 待确认记录（可能为空）
     */
    public Optional<PendingConfirm> lookup(String userId, String sessionId) {
        return Optional.ofNullable(entries.get(key(userId, sessionId)));
    }

    /**
     * 消费并移除待确认请求（批准/拒绝提交后调用）。
     *
     * @param userId    复合 userId
     * @param sessionId 复合 sessionId
     */
    public void remove(String userId, String sessionId) {
        entries.remove(key(userId, sessionId));
    }

    private String key(String userId, String sessionId) {
        return userId + KEY_SEPARATOR + sessionId;
    }
}
