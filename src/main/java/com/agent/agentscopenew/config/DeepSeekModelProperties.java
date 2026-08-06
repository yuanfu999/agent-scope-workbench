package com.agent.agentscopenew.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * DeepSeek 模型全局配置（application.yml → workbench.deepseek.*）。
 * <p>
 * 通过 {@link com.agent.agentscopenew.model.DeepSeekModelProvider#configure(String, String)}
 * 注入 SPI 提供商，用于替代或兜底环境变量 {@code DEEPSEEK_API_KEY} /
 * {@code DEEPSEEK_BASE_URL}，避免 IDE 或旧终端启动时环境变量不生效的问题。
 */
@ConfigurationProperties(prefix = "workbench.deepseek")
public record DeepSeekModelProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("") String baseUrl) {
}
