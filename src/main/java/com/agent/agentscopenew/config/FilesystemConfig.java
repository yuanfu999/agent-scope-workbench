package com.agent.agentscopenew.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 文件系统配置模型。
 */
public record FilesystemConfig(
        @DefaultValue("LOCAL") String mode,
        @DefaultValue("USER") String isolationScope,
        @DefaultValue("_default") String anonymousUserId) {

    /**
     * 判断是否为远程模式。
     */
    public boolean isRemote() {
        return "REMOTE".equalsIgnoreCase(mode);
    }

    /**
     * 判断是否为沙箱模式。
     */
    public boolean isSandbox() {
        return "SANDBOX".equalsIgnoreCase(mode);
    }

    /**
     * 判断是否为本地模式。
     */
    public boolean isLocal() {
        return "LOCAL".equalsIgnoreCase(mode);
    }

    /**
     * 默认文件系统配置（本地模式）。
     */
    public static FilesystemConfig defaults() {
        return new FilesystemConfig("LOCAL", "USER", "_default");
    }
}