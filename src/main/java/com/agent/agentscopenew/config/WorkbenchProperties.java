package com.agent.agentscopenew.config;

import com.agent.agentscopenew.dto.response.ModelInfo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.ArrayList;
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
     * 派生可选模型列表（按配置顺序）。
     * <p>
     * 每个 Agent 对应一个可选模型：模型 ID 取配置 model 中冒号后的部分
     * （如 {@code deepseek:deepseek-v4-pro} → {@code deepseek-v4-pro}），
     * 展示名优先取 label 配置，缺省回退到模型 ID。
     *
     * @return 模型列表，未配置 Agent 时返回空列表
     */
    public List<ModelInfo> listModels() {
        if (agents == null || agents.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelInfo> models = new ArrayList<>(agents.size());
        for (AgentProperties ap : agents) {
            String id = modelIdOf(ap.model());
            String label = (ap.label() == null || ap.label().isBlank()) ? id : ap.label();
            models.add(new ModelInfo(id, label, ap.name()));
        }
        return models;
    }

    /**
     * 从完整模型 ID 中提取 API 模型名（去掉提供商标识前缀）。
     */
    private String modelIdOf(String model) {
        if (model == null) {
            return "";
        }
        int colon = model.indexOf(':');
        return colon >= 0 ? model.substring(colon + 1) : model;
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