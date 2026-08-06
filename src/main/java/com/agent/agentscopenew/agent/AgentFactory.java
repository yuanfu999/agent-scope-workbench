package com.agent.agentscopenew.agent;

import com.agent.agentscopenew.config.AgentProperties;
import com.agent.agentscopenew.config.FilesystemConfig;
import com.agent.agentscopenew.config.SandboxConfig;
import com.agent.agentscopenew.config.WorkbenchProperties;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 工厂：配置模型 → HarnessAgent 实例。
 * <p>
 * 这是整个平台的核心组装点，负责将 {@link AgentProperties} 映射为
 * {@link HarnessAgent.Builder} 的完整调用链。
 */
public final class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    /** 默认记忆 Flush 间隔（配置解析失败时兜底）。 */
    private static final Duration DEFAULT_FLUSH_GAP = Duration.ofMinutes(10);

    /** 简化 Duration 匹配模式：数字 + 单位（MS/S/M/H/D）。 */
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*(MS|S|M|H|D)");

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

        // 记忆配置（消费 yml 中 flush-trigger / model 参数）
        if (p.memoryEnabled()) {
            MemoryConfig memoryConfig = MemoryConfig.builder()
                    .flushTrigger(parseFlushTrigger(p.flushTrigger()))
                    .model(p.resolveMemoryModel())
                    .build();
            builder.memory(memoryConfig);
            log.info("Agent [{}] 记忆已启用, flushTrigger={}, model={}",
                    p.name(), p.flushTrigger(), p.resolveMemoryModel());
        }

        // 上下文压缩
        if (p.compactionEnabled()) {
            builder.compaction(CompactionConfig.builder()
                    .triggerMessages(p.triggerMessages())
                    .keepMessages(p.keepMessages())
                    .build());
            builder.toolResultEviction(ToolResultEvictionConfig.defaults());
        }

        // 技能市场（按配置顺序叠加，后注册覆盖先注册）
        applySkillRepositories(builder, p);

        // 子 Agent 声明（编程式内置，按配置过滤）
        applySubagents(builder, p);

        // 分布式存储（prod 模式下）
        applyDistributedStore(builder, p);

        // 构建期校验
        HarnessAgent agent = builder.build();
        log.info("Agent [{}] 构建完成, model={}, filesystem={}", p.name(), p.model(), fsConfig.mode());
        return agent;
    }

    /**
     * 注册技能市场。
     * <p>
     * 按 {@code skills.repositories} 配置顺序叠加：classpath 内置技能
     * （resources/skills）立即注册；Git 团队仓库需额外扩展构件，当前仅告警。
     *
     * @param builder Agent 构建器
     * @param p       Agent 配置
     */
    private void applySkillRepositories(HarnessAgent.Builder builder, AgentProperties p) {
        List<String> repositories = p.skillsRepositories();
        if (repositories == null || repositories.isEmpty()) {
            return;
        }
        for (String repo : repositories) {
            switch (repo.toLowerCase()) {
                case "classpath":
                    registerClasspathRepository(builder, p);
                    break;
                case "git":
                    // GitSkillRepository 位于 agentscope-extensions-skill-git 构件，当前未引入
                    if (p.skillsGitUrl() == null || p.skillsGitUrl().isBlank()) {
                        log.warn("Agent [{}] 配置了 git 技能市场但未设置 git-url，跳过", p.name());
                    } else {
                        log.warn("Agent [{}] git 技能市场已配置 [{}]，但 agentscope-extensions-skill-git 构件未引入，暂不激活",
                                p.name(), p.skillsGitUrl());
                    }
                    break;
                default:
                    log.warn("Agent [{}] 未知技能市场类型 [{}]，跳过", p.name(), repo);
            }
        }
    }

    /**
     * 注册 classpath 内置技能市场。
     *
     * @param builder Agent 构建器
     * @param p       Agent 配置
     */
    private void registerClasspathRepository(HarnessAgent.Builder builder, AgentProperties p) {
        try {
            ClasspathSkillRepository repository = new ClasspathSkillRepository("skills");
            builder.skillRepository(repository);
            log.info("Agent [{}] 注册 classpath 技能市场, 根路径=skills", p.name());
        } catch (IOException e) {
            log.warn("Agent [{}] classpath 技能市场加载失败: {}", p.name(), e.getMessage());
        }
    }

    /**
     * 注册内置子 Agent 声明。
     * <p>
     * 从 {@link SubagentCatalog} 取默认声明列表，按 {@code subagents} 配置过滤；
     * 与工作区 {@code subagents/*.md} 文件共存，同名时工作区声明优先。
     *
     * @param builder Agent 构建器
     * @param p       Agent 配置
     */
    private void applySubagents(HarnessAgent.Builder builder, AgentProperties p) {
        List<String> enabled = p.subagents();
        if (enabled == null || enabled.isEmpty()) {
            return;
        }
        int registered = 0;
        for (SubagentDeclaration declaration : SubagentCatalog.defaultDeclarations()) {
            if (enabled.contains(declaration.getName())) {
                builder.subagent(declaration);
                registered++;
            }
        }
        log.info("Agent [{}] 注册内置子 Agent {} 个: {}", p.name(), registered, enabled);
    }

    /**
     * 解析 flushTrigger 配置字符串。
     * <p>
     * 支持 ALWAYS / NEVER / THROTTLED(Duration)，Duration 支持 ISO-8601
     * （PT10M）与简化写法（10m/30s/1h）。解析失败时回退到默认间隔。
     *
     * @param raw 配置值
     * @return FlushTrigger 实例
     */
    private MemoryConfig.FlushTrigger parseFlushTrigger(String raw) {
        if (raw == null || raw.isBlank()) {
            return MemoryConfig.FlushTrigger.throttled(DEFAULT_FLUSH_GAP);
        }
        String value = raw.trim().toUpperCase();
        if ("ALWAYS".equals(value)) {
            return MemoryConfig.FlushTrigger.always();
        }
        if ("NEVER".equals(value)) {
            return MemoryConfig.FlushTrigger.never();
        }
        if (value.startsWith("THROTTLED(") && value.endsWith(")")) {
            String durationStr = value.substring("THROTTLED(".length(), value.length() - 1);
            Duration duration = parseDuration(durationStr);
            if (duration != null) {
                return MemoryConfig.FlushTrigger.throttled(duration);
            }
        }
        log.warn("无法解析 flushTrigger [{}]，回退到 THROTTLED({})", raw, DEFAULT_FLUSH_GAP);
        return MemoryConfig.FlushTrigger.throttled(DEFAULT_FLUSH_GAP);
    }

    /**
     * 解析 Duration 字符串，支持 ISO-8601（PT10M）与简化写法（10m/30s/1h）。
     *
     * @param raw 配置值
     * @return 解析成功的 Duration，失败返回 null
     */
    private Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("P") || value.startsWith("p")) {
            try {
                return Duration.parse(value);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        Matcher matcher = DURATION_PATTERN.matcher(value.toUpperCase());
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            return switch (unit) {
                case "MS" -> Duration.ofMillis(amount);
                case "S" -> Duration.ofSeconds(amount);
                case "M" -> Duration.ofMinutes(amount);
                case "H" -> Duration.ofHours(amount);
                case "D" -> Duration.ofDays(amount);
                default -> null;
            };
        }
        return null;
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