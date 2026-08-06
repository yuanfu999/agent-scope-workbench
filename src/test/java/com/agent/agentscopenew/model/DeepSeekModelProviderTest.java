package com.agent.agentscopenew.model;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.spi.ModelProvider;
import io.agentscope.extensions.model.openai.OpenAIChatModel;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeekModelProvider 单元测试。
 * <p>
 * 验证 SPI 注册生效、deepseek: 前缀解析、API Key 缺失时的构建期校验。
 */
class DeepSeekModelProviderTest {

    @Test
    void spiRegistersDeepSeekProvider() {
        boolean registered = ServiceLoader.load(ModelProvider.class).stream()
                .anyMatch(p -> DeepSeekModelProvider.class.getName().equals(p.type().getName()));
        assertTrue(registered, "DeepSeekModelProvider 应通过 SPI 注册");
    }

    @Test
    void supportsOnlyDeepSeekPrefix() {
        DeepSeekModelProvider provider = new DeepSeekModelProvider();
        assertTrue(provider.supports("deepseek:deepseek-chat"));
        assertTrue(provider.supports("deepseek:deepseek-reasoner"));
        assertFalse(provider.supports("openai:gpt-4o"));
        assertFalse(provider.supports(null));
        assertEquals("deepseek", provider.providerId());
    }

    @Test
    void createUsesContextApiKey() {
        DeepSeekModelProvider provider = new DeepSeekModelProvider();
        Model model = provider.create("deepseek:deepseek-chat",
                ModelCreationContext.builder().apiKey("sk-test").build());
        assertNotNull(model);
        assertInstanceOf(OpenAIChatModel.class, model);
        assertEquals("deepseek-chat", ((OpenAIChatModel) model).getModelName());
    }

    @Test
    void createWithoutApiKeyThrows() {
        // 仅当环境变量未设置时断言成立（本地未配 DEEPSEEK_API_KEY 的机器）
        if (System.getenv("DEEPSEEK_API_KEY") != null) {
            return;
        }
        DeepSeekModelProvider provider = new DeepSeekModelProvider();
        assertThrows(IllegalStateException.class, () -> provider.create("deepseek:deepseek-chat"));
    }

    @Test
    void unsupportedModelIdThrows() {
        DeepSeekModelProvider provider = new DeepSeekModelProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.create("openai:gpt-4o"));
    }
}
