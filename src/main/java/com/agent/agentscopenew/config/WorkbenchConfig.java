package com.agent.agentscopenew.config;

import com.agent.agentscopenew.agent.AgentFactory;
import com.agent.agentscopenew.agent.AgentRegistry;
import com.agent.agentscopenew.model.DeepSeekModelProvider;
import com.agent.agentscopenew.security.ApiKeyFilter;
import com.agent.agentscopenew.tracing.TracingSupport;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
     * 分布式存储提供方（FR-10.1）。
     * <p>
     * Redis 模式构造期即做 ping 连通性验证，不可达时启动失败并给出配置指引。
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedStoreProvider distributedStoreProvider(WorkbenchProperties workbenchProperties) {
        return new DistributedStoreProvider(workbenchProperties.store());
    }

    /**
     * Agent 工厂。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentFactory agentFactory(WorkbenchProperties workbenchProperties,
            DistributedStoreProvider storeProvider) {
        return new AgentFactory(workbenchProperties, storeProvider);
    }

    /**
     * OTel 可观测性装配（FR-10.5）。
     * <p>
     * 仅当 workbench.observability.enabled=true 时创建；构造期即完成
     * TracerRegistry 注册与 tracing hook 挂载。
     */
    @Bean
    @ConditionalOnProperty(prefix = "workbench.observability", name = "enabled", havingValue = "true")
    public TracingSupport tracingSupport(WorkbenchProperties workbenchProperties) {
        WorkbenchProperties.ObservabilityConfig observability = workbenchProperties.observability();
        return new TracingSupport(observability.otlpEndpoint(), observability.serviceName());
    }

    /**
     * Agent 注册表与 Gateway 管理器。
     * <p>
     * 创建 Agent 前将 deepseek 配置注入 SPI 提供商，保证模型构建阶段即可读到密钥；
     * 通过 ObjectProvider 触发 TracingSupport 初始化，保证 Agent 构建前 TracerRegistry 完成注册（FR-10.5）。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry(WorkbenchProperties workbenchProperties, AgentFactory agentFactory,
            DeepSeekModelProperties deepSeekModelProperties, ObjectProvider<TracingSupport> tracingSupport) {
        // observability.enabled=true 时实例化 TracingSupport（构造即注册），否则为空
        tracingSupport.ifAvailable(ts -> {
        });
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