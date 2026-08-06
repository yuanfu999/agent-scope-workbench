package com.agent.agentscopenew.agent;

import com.agent.agentscopenew.config.AgentProperties;
import com.agent.agentscopenew.config.FilesystemConfig;
import com.agent.agentscopenew.config.SandboxConfig;
import com.agent.agentscopenew.config.WorkbenchProperties;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.DistributedStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * Agent 工厂：配置模型 → HarnessAgent 实例。
 * <p>
 * 这是整个平台的核心组装点，负责将 {@link AgentProperties} 映射为
 * {@link HarnessAgent.Builder} 的完整调用链。
 */
public final class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final WorkbenchProperties workbenchConfig;

    public AgentFactory(WorkbenchProperties workbenchConfig) {
        this.workbenchConfig = workbenchConfig;
    }

    /**
     * 根据配置构建一个 HarnessAgent。
     *
     * @param p Agent 配置
     * @return 构建完成的 HarnessAgent
     * @throws IllegalStateException 如果配置校验失败
     */
    public HarnessAgent build(AgentProperties p) {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(p.name())
                .model(p.model())
                .sysPrompt(p.sysPrompt())
                .workspace(p.workspacePath())
                .maxIters(p.steps())
                .generateOptions(GenerateOptions.builder()
                        .temperature(p.temperature())
                        .topP(p.topP())
                        .build());

        // Plan Mode
        if (p.planModeEnabled()) {
            builder.enablePlanMode(true)
                    .planFileDirectory(p.planDirectory())
                    .allowShellInPlanMode(p.allowShellInPlanMode());
        }

        // 任务清单
        if (p.taskListEnabled()) {
            builder.enableTaskList(true);
        }

        // 文件系统三模式
        FilesystemConfig fsConfig = p.filesystem();
        switch (fsConfig.mode().toUpperCase()) {
            case "LOCAL":
                builder.filesystem(new LocalFilesystemSpec());
                log.info("Agent [{}] 使用本地文件系统模式", p.name());
                break;
            case "REMOTE":
                builder.filesystem(new RemoteFilesystemSpec()
                        .isolationScope(parseIsolationScope(fsConfig.isolationScope()))
                        .anonymousUserId(fsConfig.anonymousUserId()));
                log.info("Agent [{}] 使用远程共享存储模式, scope={}", p.name(), fsConfig.isolationScope());
                break;
            case "SANDBOX":
                SandboxConfig sb = p.sandbox();
                // 注意：isolationScope/workspaceProjectionEnabled 声明在基类 SandboxFilesystemSpec，
                // 必须放在 Docker 专属方法之后，否则链式调用会退化为基类类型
                builder.filesystem(new DockerFilesystemSpec()
                        .image(sb.image())
                        .memorySizeBytes(sb.memoryBytes())
                        .cpuCount(sb.cpuCount())
                        .isolationScope(parseIsolationScope(fsConfig.isolationScope()))
                        .workspaceProjectionEnabled(sb.workspaceProjectionEnabled()));
                log.info("Agent [{}] 使用 Docker 沙箱模式, image={}", p.name(), sb.image());
                break;
            default:
                builder.filesystem(new LocalFilesystemSpec());
                log.warn("Agent [{}] 未知的文件系统模式 [{}]，回退到 LOCAL", p.name(), fsConfig.mode());
        }

        // 记忆配置（使用默认配置简化）
        if (p.memoryEnabled()) {
            MemoryConfig memoryConfig = MemoryConfig.defaults();
            builder.memory(memoryConfig);
        }

        // 上下文压缩
        if (p.compactionEnabled()) {
            builder.compaction(CompactionConfig.builder()
                    .triggerMessages(p.triggerMessages())
                    .keepMessages(p.keepMessages())
                    .build());
            builder.toolResultEviction(ToolResultEvictionConfig.defaults());
        }

        // 分布式存储（prod 模式下）
        applyDistributedStore(builder, p);

        // 构建期校验
        HarnessAgent agent = builder.build();
        log.info("Agent [{}] 构建完成, model={}, filesystem={}", p.name(), p.model(), fsConfig.mode());
        return agent;
    }

    /**
     * 应用分布式存储配置。
     * <p>
     * 在 prod profile 下，根据 workbench.store.type 配置注入对应的
     * DistributedStore。
     */
    private void applyDistributedStore(HarnessAgent.Builder builder, AgentProperties p) {
        if (workbenchConfig == null) {
            return;
        }
        WorkbenchProperties.StoreConfig store = workbenchConfig.store();
        if (store == null || "json-file".equals(store.type())) {
            // dev 模式：使用本地文件存储（默认）
            return;
        }
        // 生产模式：预留扩展点，M3 里程碑实现 Redis 等分布式存储
        log.info("Agent [{}] 分布式存储配置: type={} (M3 里程碑实现)", p.name(), store.type());
    }

    /**
     * 解析 IsolationScope 字符串。
     */
    private IsolationScope parseIsolationScope(String scope) {
        if (scope == null) {
            return IsolationScope.USER;
        }
        switch (scope.toUpperCase()) {
            case "SESSION":
                return IsolationScope.SESSION;
            case "AGENT":
                return IsolationScope.AGENT;
            case "GLOBAL":
                return IsolationScope.GLOBAL;
            default:
                return IsolationScope.USER;
        }
    }
}