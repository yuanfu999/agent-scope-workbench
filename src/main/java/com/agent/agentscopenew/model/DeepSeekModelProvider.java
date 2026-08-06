package com.agent.agentscopenew.model;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.spi.ModelProvider;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.ProxyConfig;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;

import java.util.regex.Pattern;

/**
 * DeepSeek 模型提供商。
 * <p>
 * DeepSeek API 与 OpenAI 协议兼容，但官方 {@code agentscope-extensions-model-openai}
 * 提供商仅从 {@code OPENAI_API_KEY} 环境变量读取密钥，且 baseUrl 无环境变量入口，
 * 直接使用会把 DeepSeek 密钥发往 api.openai.com 导致鉴权失败。
 * <p>
 * 本类通过标准 SPI（{@code META-INF/services/io.agentscope.core.model.spi.ModelProvider}）
 * 注册 {@code deepseek:} 前缀模型。密钥与端点按优先级从高到低取：
 * <ul>
 *   <li>{@code context.getApiKey()}：模型创建上下文携带的密钥</li>
 *   <li>{@code workbench.deepseek.api-key}：Spring 配置注入（{@link #configure(String, String)}），
 *       解决 IDE/终端启动时环境变量不生效的问题</li>
 *   <li>{@code DEEPSEEK_API_KEY} / {@code DEEPSEEK_BASE_URL}：环境变量兜底，
 *       缺失时构建期抛异常</li>
 * </ul>
 * 使用方式：{@code LLM_MODEL=deepseek:deepseek-chat}（主 Agent、记忆模型、子 Agent 均生效）。
 */
public final class DeepSeekModelProvider implements ModelProvider {

    /** 模型 ID 前缀。 */
    public static final String PREFIX = "deepseek:";

    /** 默认 API 端点。 */
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /** 模型 ID 匹配模式，与官方 OpenAI 提供商保持同风格。 */
    private static final Pattern MODEL_ID = Pattern.compile("deepseek:.+");

    /** Spring 配置注入的 API 密钥（workbench.deepseek.api-key），优先级高于环境变量。 */
    private static volatile String configuredApiKey;

    /** Spring 配置注入的 API 端点（workbench.deepseek.base-url），优先级高于环境变量。 */
    private static volatile String configuredBaseUrl;

    @Override
    public String providerId() {
        return "deepseek";
    }

    @Override
    public boolean supports(String modelId) {
        return modelId != null && MODEL_ID.matcher(modelId).matches();
    }

    /**
     * 注入全局 API 密钥与端点。
     * <p>
     * 由 Spring 配置（workbench.deepseek.*）在应用启动时调用，供 SPI 创建的
     * 模型实例使用；传入 null 或空白串表示不覆盖对应项（回退到环境变量）。
     *
     * @param apiKey  API 密钥
     * @param baseUrl API 端点
     */
    public static void configure(String apiKey, String baseUrl) {
        configuredApiKey = trimToNull(apiKey);
        configuredBaseUrl = trimToNull(baseUrl);
    }

    @Override
    public Model create(String modelId) {
        return create(modelId, ModelCreationContext.empty());
    }

    @Override
    public Model create(String modelId, ModelCreationContext context) {
        if (!supports(modelId)) {
            throw new IllegalArgumentException("不支持的模型 ID: " + modelId);
        }
        String modelName = modelId.substring(PREFIX.length());
        String apiKey = firstNonBlank(context.getApiKey(), configuredApiKey,
                System.getenv("DEEPSEEK_API_KEY"));
        if (apiKey == null) {
            throw new IllegalStateException(
                    "DEEPSEEK_API_KEY 未配置（环境变量或 workbench.deepseek.api-key），无法创建 deepseek 模型: " + modelId);
        }
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .formatter(new DeepSeekFormatter())
                .stream(context.getStream() == null || context.getStream());
        String baseUrl = firstNonBlank(configuredBaseUrl, System.getenv("DEEPSEEK_BASE_URL"));
        builder.baseUrl(baseUrl == null ? DEFAULT_BASE_URL : baseUrl);
        String endpointPath = trimToNull(context.getEndpointPath());
        if (endpointPath != null) {
            builder.endpointPath(endpointPath);
        }
        applyAdvancedOptions(builder, context);
        return builder.build();
    }

    /**
     * 从上下文提取高级选项（GenerateOptions / HttpTransport / ProxyConfig），
     * 与官方 OpenAI 提供商行为对齐。
     */
    private void applyAdvancedOptions(OpenAIChatModel.Builder builder, ModelCreationContext context) {
        GenerateOptions generateOptions = context.component(GenerateOptions.class);
        if (generateOptions != null) {
            builder.generateOptions(generateOptions);
        }
        HttpTransport transport = context.component(HttpTransport.class);
        if (transport != null) {
            builder.httpTransport(transport);
        }
        ProxyConfig proxy = context.component(ProxyConfig.class);
        if (proxy != null) {
            builder.proxy(proxy);
        }
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
