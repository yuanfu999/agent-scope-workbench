package com.agent.agentscopenew.tracing;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.formatter.AbstractBaseFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.function.Supplier;

/**
 * AgentScope 框架 {@code Tracer} 的 OpenTelemetry 实现（FR-10.5）。
 * <p>
 * 在 callAgent / callModel / callTool 三类调用上创建 span（{@code agent.call} /
 * {@code model.call} / {@code tool.call}），附 agent 名 / 模型类名 / 工具名属性；
 * 调用失败时标记 ERROR 状态并 recordException。span 生命周期与响应流一致：
 * 流结束（正常/错误/取消）时关闭。
 */
@Slf4j
@RequiredArgsConstructor
public final class WorkbenchTracer implements io.agentscope.core.tracing.Tracer {

    /** Agent 调用 span 名。 */
    private static final String SPAN_AGENT = "agent.call";
    /** 模型调用 span 名。 */
    private static final String SPAN_MODEL = "model.call";
    /** 工具调用 span 名。 */
    private static final String SPAN_TOOL = "tool.call";

    /** OTel Tracer（serviceName 命名）。 */
    private final Tracer otelTracer;

    @Override
    public Mono<Msg> callAgent(AgentBase agent, List<Msg> msgs, Supplier<Mono<Msg>> next) {
        Span span = otelTracer.spanBuilder(SPAN_AGENT)
                .setAttribute("name", agent.getName())
                .startSpan();
        return next.get()
                .doOnError(e -> failSpan(span, e))
                .doFinally(signal -> span.end());
    }

    @Override
    public Flux<ChatResponse> callModel(ChatModelBase model, List<Msg> msgs, List<ToolSchema> tools,
            GenerateOptions options, Supplier<Flux<ChatResponse>> next) {
        Span span = otelTracer.spanBuilder(SPAN_MODEL)
                .setAttribute("model", model.getClass().getSimpleName())
                .startSpan();
        return next.get()
                .doOnError(e -> failSpan(span, e))
                .doFinally(signal -> span.end());
    }

    @Override
    public Mono<ToolResultBlock> callTool(Toolkit toolkit, ToolCallParam param,
            Supplier<Mono<ToolResultBlock>> next) {
        ToolUseBlock useBlock = param.getToolUseBlock();
        Span span = otelTracer.spanBuilder(SPAN_TOOL)
                .setAttribute("toolName", useBlock != null ? useBlock.getName() : "unknown")
                .startSpan();
        return next.get()
                .doOnError(e -> failSpan(span, e))
                .doFinally(signal -> span.end());
    }

    @Override
    public <TReq, TResp, TParams> List<TReq> callFormat(AbstractBaseFormatter<TReq, TResp, TParams> formatter,
            List<Msg> msgs, Supplier<List<TReq>> next) {
        // 格式化调用不做独立 span（语义归并到 agent.call / model.call 链路）
        return next.get();
    }

    @Override
    public <TResp> TResp runWithContext(ContextView contextView, Supplier<TResp> supplier) {
        // Reactor 上下文透传；span 上下文由框架 Hook 保持
        return supplier.get();
    }

    @Override
    public void shutdown() {
        // SdkTracerProvider 生命周期由 TracingSupport 统一管理
    }

    /**
     * 标记 span 失败并记录异常。
     */
    private void failSpan(Span span, Throwable error) {
        span.setStatus(StatusCode.ERROR);
        span.recordException(error);
    }
}
