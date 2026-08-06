package com.agent.agentscopenew.config;

import com.agent.agentscopenew.agent.AgentFactory;
import com.agent.agentscopenew.agent.AgentRegistry;
import com.agent.agentscopenew.model.DeepSeekModelProvider;
import com.agent.agentscopenew.security.ApiKeyFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Workbench 全局 Spring 配置。
 * <p>
 * 负责装配 AgentFactory、AgentRegistry、ApiKeyFilter 等核心 Bean。
 */
@Configuration
public class WorkbenchConfig {

    /**
     * Agent 工厂。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentFactory agentFactory(WorkbenchProperties workbenchProperties) {
        return new AgentFactory(workbenchProperties);
    }

    /**
     * Agent 注册表与 Gateway 管理器。
     * <p>
     * 创建 Agent 前将 deepseek 配置注入 SPI 提供商，保证模型构建阶段即可读到密钥。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry(WorkbenchProperties workbenchProperties, AgentFactory agentFactory,
            DeepSeekModelProperties deepSeekModelProperties) {
        DeepSeekModelProvider.configure(deepSeekModelProperties.apiKey(), deepSeekModelProperties.baseUrl());
        return new AgentRegistry(workbenchProperties, agentFactory);
    }

    /**
     * API Key 鉴权过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyFilter apiKeyFilter(WorkbenchProperties workbenchProperties) {
        return new ApiKeyFilter(workbenchProperties);
    }
}