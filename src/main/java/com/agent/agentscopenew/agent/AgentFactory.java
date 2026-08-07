package com.agent.agentscopenew.agent;

import com.agent.agentscopenew.config.AgentProperties;
import com.agent.agentscopenew.config.FilesystemConfig;
import com.agent.agentscopenew.config.SandboxConfig;
import com.agent.agentscopenew.config.SandboxImageValidator;
import com.agent.agentscopenew.config.WorkbenchProperties;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxExecutionGuard;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.tool.SkillManageConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 工厂：配置模型 → HarnessAgent 实例。
 * <p>
 * 这是整个平台的核心组装点，负责将 {@link AgentProperties} 映射为
 * {@link HarnessAgent.Builder} 的完整调用链。
 */
@Slf4j
@RequiredArgsConstructor
public final class AgentFactory {

    /** 默认记忆 Flush 间隔（配置解析失败时兜底）。 */
    private static final Duration DEFAULT_FLUSH_GAP = Duration.ofMinutes(10);

    /** 简化 Duration 匹配模式：数字 + 单位（MS/S/M/H/D）。 */
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*(MS|S|M|H|D)");

    private final WorkbenchProperties workbenchConfig;

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
                // 镜像基线校验（FR-3.4）：Docker 不可达或镜像缺工具链时启动即报错
                if (sb.checkOnStart()) {
                    SandboxImageValidator.validate(sb.image());
                }
                DockerFilesystemSpec dockerSpec = new DockerFilesystemSpec()
                        .image(sb.image())
                        .memorySizeBytes(sb.memoryBytes())
                        .cpuCount(sb.cpuCount());
                // Docker 专属配置（FR-3.1），未配置的字段跳过（链式调用保持 Docker 类型）
                if (sb.workspaceRoot() != null && !sb.workspaceRoot().isBlank()) {
                    dockerSpec.workspaceRoot(sb.workspaceRoot());
                }
                if (sb.environment() != null && !sb.environment().isEmpty()) {
                    dockerSpec.environment(sb.environment());
                }
                if (sb.exposedPorts() != null && !sb.exposedPorts().isEmpty()) {
                    dockerSpec.exposedPorts(sb.exposedPorts().stream()
                            .mapToInt(Integer::intValue).toArray());
                }
                if (sb.network() != null && !sb.network().isBlank()) {
                    dockerSpec.network(sb.network());
                }
                if (sb.additionalRunArgs() != null && !sb.additionalRunArgs().isEmpty()) {
                    dockerSpec.additionalRunArgs(sb.additionalRunArgs());
                }
                // 快照策略（FR-3.2）：snapshotSpec 在 Docker 类上有协变重写，仍在专属方法区
                dockerSpec.snapshotSpec(buildSnapshotSpec(sb));
                // 注意：isolationScope/executionGuard/workspaceProjectionEnabled/
                // workspaceProjectionRoots 声明在基类 SandboxFilesystemSpec，
                // 必须放在 Docker 专属方法之后，否则链式调用会退化为基类类型
                dockerSpec.executionGuard(buildExecutionGuard(sb))
                        .isolationScope(parseIsolationScope(fsConfig.isolationScope()))
                        .workspaceProjectionEnabled(sb.workspaceProjectionEnabled())
                        .workspaceProjectionRoots(sb.projectionRootsOrDefault());
                builder.filesystem(dockerSpec);
                log.info("Agent [{}] 使用 Docker 沙箱模式, image={}, snapshot={}, projectionRoots={}",
                        p.name(), sb.image(), sb.snapshotType(), sb.projectionRootsOrDefault());
                break;
            default:
                builder.filesystem(new LocalFilesystemSpec());
                log.warn("Agent [{}] 未知的文件系统模式 [{}]，回退到 LOCAL", p.name(), fsConfig.mode());
        }

        // 记忆配置（消费 yml 中 flush-trigger / model / 定制位参数）
        if (p.memoryEnabled()) {
            MemoryConfig.Builder memoryBuilder = MemoryConfig.builder()
                    .flushTrigger(parseFlushTrigger(p.flushTrigger()))
                    .model(p.resolveMemoryModel());
            // 定制位：仅显式配置（非空/非负）时覆盖框架默认值
            if (!p.memoryFlushPrompt().isBlank()) {
                memoryBuilder.flushPrompt(p.memoryFlushPrompt());
            }
            if (!p.memoryConsolidationPrompt().isBlank()) {
                memoryBuilder.consolidationPrompt(p.memoryConsolidationPrompt());
            }
            if (p.memoryConsolidationMaxTokens() >= 0) {
                memoryBuilder.consolidationMaxTokens(p.memoryConsolidationMaxTokens());
            }
            if (!p.memoryConsolidationMinGap().isBlank()) {
                Duration gap = parseDuration(p.memoryConsolidationMinGap());
                if (gap != null) {
                    memoryBuilder.consolidationMinGap(gap);
                } else {
                    log.warn("Agent [{}] 无法解析 consolidation-min-gap [{}]，使用框架默认",
                            p.name(), p.memoryConsolidationMinGap());
                }
            }
            if (p.memoryDailyFileRetentionDays() >= 0) {
                memoryBuilder.dailyFileRetentionDays(p.memoryDailyFileRetentionDays());
            }
            if (p.memorySessionRetentionDays() >= 0) {
                memoryBuilder.sessionRetentionDays(p.memorySessionRetentionDays());
            }
            builder.memory(memoryBuilder.build());
            log.info("Agent [{}] 记忆已启用, flushTrigger={}, model={}",
                    p.name(), p.flushTrigger(), p.resolveMemoryModel());
        }

        // 记忆完全关闭开关（FR-5.6）
        if (p.disableMemoryTools()) {
            builder.disableMemoryTools();
            log.info("Agent [{}] 记忆工具已关闭（memory_search/session_list 等不再注册）", p.name());
        }
        if (p.disableMemoryHooks()) {
            builder.disableMemoryHooks();
            log.info("Agent [{}] 记忆后台维护已关闭（日归档/整合不再自动执行）", p.name());
        }

        // 上下文压缩（trigger-tokens 为溢出兜底：上下文接近上限时自动压缩，FR-6.2）
        if (p.compactionEnabled()) {
            CompactionConfig.Builder compactionBuilder = CompactionConfig.builder()
                    .triggerMessages(p.triggerMessages())
                    .keepMessages(p.keepMessages());
            if (p.compactionTriggerTokens() >= 0) {
                compactionBuilder.triggerTokens(p.compactionTriggerTokens());
            }
            if (p.compactionKeepTokens() >= 0) {
                compactionBuilder.keepTokens(p.compactionKeepTokens());
            }
            if (!p.compactionSummaryPrompt().isBlank()) {
                compactionBuilder.summaryPrompt(p.compactionSummaryPrompt());
            }
            String compactionModelRaw = p.compactionModel();
            if (compactionModelRaw != null && !compactionModelRaw.isBlank()
                    && !"@{}".equals(compactionModelRaw)) {
                compactionBuilder.model(p.resolveCompactionModel());
            }
            builder.compaction(compactionBuilder.build());

            // 工具结果卸载（FR-6.3）：显式配置时覆盖默认，否则用框架默认
            if (p.evictionMaxResultChars() >= 0 || p.evictionPreviewChars() >= 0
                    || !p.evictionExcludedTools().isBlank()) {
                ToolResultEvictionConfig.Builder evictionBuilder = ToolResultEvictionConfig.builder();
                if (p.evictionMaxResultChars() >= 0) {
                    evictionBuilder.maxResultChars(p.evictionMaxResultChars());
                }
                if (p.evictionPreviewChars() >= 0) {
                    evictionBuilder.previewChars(p.evictionPreviewChars());
                }
                if (!p.evictionExcludedTools().isBlank()) {
                    evictionBuilder.excludedToolNames(parseToolNames(p.evictionExcludedTools()));
                }
                builder.toolResultEviction(evictionBuilder.build());
            } else {
                builder.toolResultEviction(ToolResultEvictionConfig.defaults());
            }
            log.info("Agent [{}] 上下文压缩已启用, triggerMessages={}, triggerTokens={}, keepMessages={}",
                    p.name(), p.triggerMessages(),
                    p.compactionTriggerTokens() >= 0 ? p.compactionTriggerTokens() : "默认",
                    p.keepMessages());
        }

        // 技能市场（按配置顺序叠加，后注册覆盖先注册）
        applySkillRepositories(builder, p);

        // 技能自学闭环开关（FR-4.7）：注册 skill 管理工具（草稿/提升闸门）
        if (p.skillManageEnabled()) {
            builder.enableSkillManageTool(SkillManageConfig.defaults());
            log.info("Agent [{}] 技能自学闭环已开启（skill 管理工具注册）", p.name());
        }

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
     * 构建沙箱快照策略（FR-3.2）。
     * <p>
     * NONE → NoopSnapshotSpec；LOCAL → LocalSnapshotSpec（快照根目录可配）；
     * REMOTE → 暂不激活（M5 由 DistributedStore 自动注入），回退 Noop 并告警。
     *
     * @param sb 沙箱配置
     * @return 快照规格
     */
    private SandboxSnapshotSpec buildSnapshotSpec(SandboxConfig sb) {
        String type = sb.snapshotType() == null ? "LOCAL" : sb.snapshotType().toUpperCase();
        switch (type) {
            case "NONE":
                return new NoopSnapshotSpec();
            case "REMOTE":
                log.warn("Agent 沙箱配置了 REMOTE 快照，由 M5 DistributedStore 自动注入，当前回退 Noop");
                return new NoopSnapshotSpec();
            default:
                return new LocalSnapshotSpec(sb.snapshotBasePath());
        }
    }

    /**
     * 构建沙箱并发守卫（FR-3.3）。
     * <p>
     * Redis 守卫由 M5 DistributedStore 自动注入；当前统一使用 noop，
     * guardEnabled 仅作为配置位并输出告警，避免误用。
     *
     * @param sb 沙箱配置
     * @return 守卫实例
     */
    private SandboxExecutionGuard buildExecutionGuard(SandboxConfig sb) {
        if (sb.guardEnabled()) {
            log.warn("Agent 沙箱启用了并发守卫配置位，Redis 守卫由 M5 DistributedStore 注入，当前使用 noop");
        }
        return SandboxExecutionGuard.noop();
    }

    /**
     * 解析工具结果卸载排除工具列表（逗号分隔）。
     *
     * @param raw 配置值，如 "write_file,edit_file"
     * @return 去空白后的工具名集合
     */
    private Set<String> parseToolNames(String raw) {
        Set<String> names = new HashSet<>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
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
        // 生产模式：预留扩展点，M5 里程碑实现 Redis 等分布式存储（并自动注入沙箱守卫）
        log.info("Agent [{}] 分布式存储配置: type={} (M5 里程碑实现)", p.name(), store.type());
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