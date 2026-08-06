package com.agent.agentscopenew.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Collections;
import java.util.List;

/**
 * 平台全局配置模型（application.yml → workbench.*）。
 */
@ConfigurationProperties(prefix = "workbench")
public record WorkbenchProperties(
        @DefaultValue("_default") String apiKey,
        @DefaultValue("rnd-assistant") String mainAgent,
        @DefaultValue("dev") String activeProfile,
        StoreConfig store,
        List<AgentProperties> agents) {

    /**
     * 获取配置的第一个 Agent，若 agents 为空则返回空构造的默认 Agent。
     */
    public AgentProperties firstAgent() {
        if (agents == null || agents.isEmpty()) {
            return AgentProperties.builder().build();
        }
        return agents.get(0);
    }

    /**
     * 根据 name 查找 Agent 配置。
     */
    public AgentProperties findAgent(String name) {
        if (agents != null) {
            for (AgentProperties ap : agents) {
                if (ap.name().equals(name)) {
                    return ap;
                }
            }
        }
        return firstAgent();
    }

    /**
     * 存储配置。
     */
    public record StoreConfig(
            @DefaultValue("json-file") String type,
            @DefaultValue("") String redisUrl) {
    }

    /**
     * 获取默认 WorkbenchProperties，用于测试或未配置时的兜底。
     */
    public static WorkbenchProperties defaults() {
        return new WorkbenchProperties(
                "_default", "rnd-assistant", "dev",
                new StoreConfig("json-file", ""),
                List.of(AgentProperties.defaults()));
    }
}