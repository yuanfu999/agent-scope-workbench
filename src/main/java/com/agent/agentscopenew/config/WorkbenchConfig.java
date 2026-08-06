package com.agent.agentscopenew.config;

import com.agent.agentscopenew.agent.AgentFactory;
import com.agent.agentscopenew.agent.AgentRegistry;
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
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry(WorkbenchProperties workbenchProperties, AgentFactory agentFactory) {
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