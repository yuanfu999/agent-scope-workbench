package com.agent.agentscopenew.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 沙箱配置模型（Docker 后端）。
 */
public record SandboxConfig(
        @DefaultValue("ubuntu:24.04") String image,
        @DefaultValue("512") long memoryMb,
        @DefaultValue("2") long cpuCount,
        @DefaultValue("") String network,
        @DefaultValue("LOCAL") String snapshotType,
        @DefaultValue("true") boolean workspaceProjectionEnabled) {

    /**
     * 获取内存字节数。
     */
    public long memoryBytes() {
        return memoryMb * 1024 * 1024;
    }

    /**
     * 默认沙箱配置。
     */
    public static SandboxConfig defaults() {
        return new SandboxConfig("ubuntu:24.04", 512, 2, "", "LOCAL", true);
    }
}