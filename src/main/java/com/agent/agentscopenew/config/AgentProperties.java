package com.agent.agentscopenew.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * 单 Agent 配置模型（workbench.agents[] 元素）。
 */
public record AgentProperties(
        @DefaultValue("rnd-assistant") String name,
        @DefaultValue("openai:gpt-4o") String model,
        @DefaultValue("你是一个有用的 AI 助手。") String sysPrompt,
        @DefaultValue("") String workspace,
        @DefaultValue("30") int steps,
        @DefaultValue("0.7") double temperature,
        @DefaultValue("0.95") double topP,
        @DefaultValue("@{" + "}" + "") String memoryModel,
        @DefaultValue("THROTTLED(10m)") String flushTrigger,
        @DefaultValue("true") boolean memoryEnabled,
        @DefaultValue("true") boolean compactionEnabled,
        @DefaultValue("40") int triggerMessages,
        @DefaultValue("10") int keepMessages,
        @DefaultValue("true") boolean planModeEnabled,
        @DefaultValue("false") boolean allowShellInPlanMode,
        @DefaultValue("plans") String planDirectory,
        @DefaultValue("true") boolean taskListEnabled,
        FilesystemConfig filesystem,
        SandboxConfig sandbox,
        @DefaultValue({"classpath", "git"}) List<String> skillsRepositories,
        @DefaultValue("") String skillsGitUrl,
        @DefaultValue({"reviewer", "researcher", "note-taker"}) List<String> subagents) {

    /**
     * 获取工作区路径。
     */
    public Path workspacePath() {
        if (workspace == null || workspace.isBlank()) {
            return Paths.get(".agentscope/workspace");
        }
        return Paths.get(workspace);
    }

    /**
     * 获取记忆辅助模型，若未设置（含哨兵默认值 "@{}"）则使用主模型。
     * <p>
     * 哨兵 "@{}" 是 memory.model 的默认占位：当 yml 中 ${MEMORY_MODEL:}
     * 解析为空值且未显式配置时，Spring Boot 会将 record 组件绑定为该默认值，
     * 此处需识别并回退，避免将非法模型名传入 MemoryConfig。
     */
    public String resolveMemoryModel() {
        if (memoryModel == null || memoryModel.isBlank() || "@{}".equals(memoryModel)) {
            return model;
        }
        return memoryModel;
    }

    /**
     * 默认 Agent 配置，用于测试/兜底。
     */
    public static AgentProperties defaults() {
        return new AgentProperties(
                "rnd-assistant", "openai:gpt-4o",
                "你是一个有用的 AI 助手。",
                "", 30, 0.7, 0.95,
                null, "THROTTLED(10m)", true, true,
                40, 10, true, false, "plans", true,
                FilesystemConfig.defaults(),
                SandboxConfig.defaults(),
                List.of("classpath", "git"), "",
                List.of("reviewer", "researcher", "note-taker"));
    }

    /**
     * 构造器，用于 builder 风格。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "rnd-assistant";
        private String model = "openai:gpt-4o";
        private String sysPrompt = "你是一个有用的 AI 助手。";
        private String workspace = "";
        private int steps = 30;
        private double temperature = 0.7;
        private double topP = 0.95;
        private String memoryModel;
        private String flushTrigger = "THROTTLED(10m)";
        private boolean memoryEnabled = true;
        private boolean compactionEnabled = true;
        private int triggerMessages = 40;
        private int keepMessages = 10;
        private boolean planModeEnabled = true;
        private boolean allowShellInPlanMode = false;
        private String planDirectory = "plans";
        private boolean taskListEnabled = true;
        private FilesystemConfig filesystem = FilesystemConfig.defaults();
        private SandboxConfig sandbox = SandboxConfig.defaults();
        private List<String> skillsRepositories = List.of("classpath", "git");
        private String skillsGitUrl = "";
        private List<String> subagents = List.of("reviewer", "researcher", "note-taker");

        public Builder name(String name) { this.name = name; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder sysPrompt(String sysPrompt) { this.sysPrompt = sysPrompt; return this; }
        public Builder workspace(String workspace) { this.workspace = workspace; return this; }
        public Builder steps(int steps) { this.steps = steps; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder topP(double topP) { this.topP = topP; return this; }
        public Builder planModeEnabled(boolean planModeEnabled) { this.planModeEnabled = planModeEnabled; return this; }
        public Builder filesystem(FilesystemConfig filesystem) { this.filesystem = filesystem; return this; }
        public Builder sandbox(SandboxConfig sandbox) { this.sandbox = sandbox; return this; }
        public Builder skillsRepositories(List<String> skillsRepositories) { this.skillsRepositories = skillsRepositories; return this; }
        public Builder subagents(List<String> subagents) { this.subagents = subagents; return this; }

        public AgentProperties build() {
            return new AgentProperties(name, model, sysPrompt, workspace, steps, temperature,
                    topP, memoryModel, flushTrigger, memoryEnabled, compactionEnabled,
                    triggerMessages, keepMessages, planModeEnabled, allowShellInPlanMode,
                    planDirectory, taskListEnabled, filesystem, sandbox,
                    skillsRepositories, skillsGitUrl, subagents);
        }
    }
}