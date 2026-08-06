package com.agent.agentscopenew.model;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.spi.ModelProvider;
import io.agentscope.extensions.model.openai.OpenAIChatModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeekModelProvider 单元测试。
 * <p>
 * 验证 SPI 注册生效、deepseek: 前缀解析、API Key 缺失时的构建期校验、
 * 以及 Spring 配置注入（configure）优先于环境变量的行为。
 */
class DeepSeekModelProviderTest {

    /**
     * 每个用例结束后重置静态注入配置，避免测试间相互污染。
     */
    @AfterEach
    void resetConfiguredState() {
        DeepSeekModelProvider.configure(null, null);
    }

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
    void createUsesConfiguredApiKeyWhenContextEmpty() {
        // 通过 configure 注入配置密钥后，无需环境变量即可创建模型
        DeepSeekModelProvider.configure("sk-configured", null);
        DeepSeekModelProvider provider = new DeepSeekModelProvider();
        Model model = provider.create("deepseek:deepseek-chat");
        assertNotNull(model);
        assertInstanceOf(OpenAIChatModel.class, model);
    }

    @Test
    void configureBlankValuesFallBackToEnvironment() {
        // 空白配置不覆盖环境变量，与未注入行为一致
        DeepSeekModelProvider.configure("   ", "");
        DeepSeekModelProvider provider = new DeepSeekModelProvider();
        if (System.getenv("DEEPSEEK_API_KEY") == null) {
            assertThrows(IllegalStateException.class, () -> provider.create("deepseek:deepseek-chat"));
        } else {
            assertNotNull(provider.create("deepseek:deepseek-chat"));
        }
    }

    @Test
    void unsupportedModelIdThrows() {
        DeepSeekModelProvider provider = new DeepSeekModelProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.create("openai:gpt-4o"));
    }
}
