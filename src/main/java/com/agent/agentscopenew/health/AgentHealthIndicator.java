package com.agent.agentscopenew.health;

import com.agent.agentscopenew.agent.AgentRegistry;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Agent 注册健康指示器（FR-10.6）。
 * <p>
 * 报告已注册 Agent 数量、名称列表与主 Agent；AgentRegistry 就绪即视为 UP，
 * 供 /actuator/health 展示（show-details=always）。
 */
@Component
public class AgentHealthIndicator implements HealthIndicator {

    private final AgentRegistry agentRegistry;

    public AgentHealthIndicator(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("agentCount", agentRegistry.agentCount())
                .withDetail("agents", agentRegistry.getAllAgents().keySet().stream()
                        .sorted().collect(Collectors.toList()))
                .withDetail("mainAgent", agentRegistry.getMainAgentName())
                .build();
    }
}
