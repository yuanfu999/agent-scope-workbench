package com.agent.agentscopenew.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * 沙箱配置模型（Docker 后端）。
 * <p>
 * 覆盖 FR-3.1~FR-3.5 接线位：镜像/资源/网络/端口/环境变量/工作区根目录（FR-3.1）、
 * 快照策略（FR-3.2）、并发守卫（FR-3.3）、启动基线校验（FR-3.4）、投影根列表（FR-3.5）。
 */
public record SandboxConfig(
        @DefaultValue("ubuntu:24.04") String image,
        @DefaultValue("512") long memoryMb,
        @DefaultValue("2") long cpuCount,
        @DefaultValue("") String network,
        @DefaultValue("LOCAL") String snapshotType,
        @DefaultValue("true") boolean workspaceProjectionEnabled,
        @DefaultValue(".agentscope/snapshots") String snapshotBasePath,
        @DefaultValue("false") boolean guardEnabled,
        @DefaultValue("true") boolean checkOnStart,
        @DefaultValue("") String workspaceRoot,
        List<Integer> exposedPorts,
        Map<String, String> environment,
        List<String> additionalRunArgs,
        List<String> projectionRoots) {

    /** 默认投影根列表：随工作区投影进容器的顶层条目（FR-3.5）。 */
    private static final List<String> DEFAULT_PROJECTION_ROOTS =
            List.of("AGENTS.md", "skills", "subagents", "knowledge");

    /**
     * 获取内存字节数。
     */
    public long memoryBytes() {
        return memoryMb * 1024 * 1024;
    }

    /**
     * 获取投影根列表，未配置时使用默认四类（AGENTS.md/skills/subagents/knowledge）。
     *
     * @return 投影根列表
     */
    public List<String> projectionRootsOrDefault() {
        if (projectionRoots == null || projectionRoots.isEmpty()) {
            return DEFAULT_PROJECTION_ROOTS;
        }
        return projectionRoots;
    }

    /**
     * 默认沙箱配置。
     */
    public static SandboxConfig defaults() {
        return new SandboxConfig("ubuntu:24.04", 512, 2, "", "LOCAL", true,
                ".agentscope/snapshots", false, true, "", null, null, null, null);
    }
}