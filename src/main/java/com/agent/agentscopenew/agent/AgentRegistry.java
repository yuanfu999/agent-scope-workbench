package com.agent.agentscopenew.agent;

import com.agent.agentscopenew.config.AgentProperties;
import com.agent.agentscopenew.config.WorkbenchProperties;

import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册表与 Gateway 管理器。
 * <p>
 * 负责注册所有 Agent 到 {@link GatewayBootstrap}，并提供
 * {@link ChatUiChannel} 作为统一的会话路由入口。
 */
public final class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, HarnessAgent> agents = new ConcurrentHashMap<>();
    private final GatewayBootstrap gateway;
    private final ChatUiChannel chatUiChannel;
    private final String mainAgentName;

    /**
     * 构造 AgentRegistry，根据配置构建所有 Agent 并注册到 Gateway。
     *
     * @param workbenchConfig 平台配置
     * @param agentFactory    Agent 工厂
     */
    public AgentRegistry(WorkbenchProperties workbenchConfig, AgentFactory agentFactory) {
        this.mainAgentName = workbenchConfig.mainAgent();

        GatewayBootstrap.Builder gwBuilder = GatewayBootstrap.builder();

        if (workbenchConfig.agents() != null) {
            for (AgentProperties ap : workbenchConfig.agents()) {
                HarnessAgent agent = agentFactory.build(ap);
                agents.put(ap.name(), agent);
                gwBuilder.agent(ap.name(), agent);
                log.info("注册 Agent [{}] 到 Gateway", ap.name());
            }
        }

        gwBuilder.mainAgent(mainAgentName);
        this.gateway = gwBuilder.build();
        this.chatUiChannel = this.gateway.chatUiChannel();

        log.info("Gateway 初始化完成, mainAgent={}, 已注册 Agent 数={}", mainAgentName, agents.size());
    }

    /**
     * 获取 ChatUiChannel，用于发送消息。
     */
    public ChatUiChannel getChatUiChannel() {
        return chatUiChannel;
    }

    /**
     * 获取 GatewayBootstrap 实例。
     */
    public GatewayBootstrap getGateway() {
        return gateway;
    }

    /**
     * 根据名称获取 HarnessAgent。
     */
    public HarnessAgent getAgent(String name) {
        return agents.get(name);
    }

    /**
     * 获取主 Agent。
     */
    public HarnessAgent getMainAgent() {
        return agents.get(mainAgentName);
    }

    /**
     * 获取所有已注册的 Agent 名称。
     */
    public Map<String, HarnessAgent> getAllAgents() {
        return new HashMap<>(agents);
    }

    /**
     * 获取主 Agent 名称。
     */
    public String getMainAgentName() {
        return mainAgentName;
    }

    /**
     * 获取已注册的 Agent 数量。
     */
    public int agentCount() {
        return agents.size();
    }
}