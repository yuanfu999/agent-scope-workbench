package com.agent.agentscopenew.tracing;

import io.agentscope.core.tracing.TracerRegistry;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import lombok.extern.slf4j.Slf4j;

/**
 * OTel 可观测性装配（FR-10.5）。
 * <p>
 * 构造期完成整条链路：OTLP gRPC 导出器 → SdkTracerProvider（BatchSpanProcessor）→
 * OpenTelemetrySdk → {@link TracerRegistry#register} + {@link TracerRegistry#enableTracingHook()}，
 * 保证任何 Agent 构建之前框架 Tracer 已就绪。仅当 {@code workbench.observability.enabled=true}
 * 时由 WorkbenchConfig 创建；dev 默认关闭，零 OTel 开销。
 */
@Slf4j
public final class TracingSupport {

    /** OpenTelemetry SDK 实例（预留：metrics/logs 扩展）。 */
    private final OpenTelemetrySdk openTelemetry;

    /**
     * 装配 OTel SDK 并注册框架 Tracer。
     *
     * @param otlpEndpoint OTLP gRPC 端点，如 {@code http://localhost:4317}
     * @param serviceName  上报服务名
     */
    public TracingSupport(String otlpEndpoint, String serviceName) {
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        Tracer otelTracer = openTelemetry.getTracer(serviceName);
        TracerRegistry.register(new WorkbenchTracer(otelTracer));
        TracerRegistry.enableTracingHook();
        log.info("OTel 可观测性已启用: service={}, otlpEndpoint={}, TracerRegistry 已注册并挂载 tracing hook",
                serviceName, otlpEndpoint);
        // 进程退出前刷出缓冲 span
        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close, "otel-tracer-shutdown"));
    }

    /**
     * 获取 OpenTelemetry SDK 实例（预留：metrics/logs 扩展）。
     */
    public OpenTelemetrySdk openTelemetry() {
        return openTelemetry;
    }
}
