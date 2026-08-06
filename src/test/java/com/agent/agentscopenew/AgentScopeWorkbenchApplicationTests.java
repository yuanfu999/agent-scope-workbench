package com.agent.agentscopenew;

import com.agent.agentscopenew.config.AgentProperties;
import com.agent.agentscopenew.config.FilesystemConfig;
import com.agent.agentscopenew.config.SandboxConfig;
import com.agent.agentscopenew.config.WorkbenchProperties;
import com.agent.agentscopenew.dto.response.ModelInfo;
import com.agent.agentscopenew.security.TenantContext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试：核心配置模型与工具类。
 * <p>
 * 注意：集成测试（需要 AgentScope 依赖）在集成测试 profile 中运行。
 */
class AgentScopeWorkbenchApplicationTests {

    @Test
    void contextLoadsPlaceholder() {
        // 占位测试，确保测试框架可用
        assertTrue(true);
    }

    @Test
    void testTenantContextUserIdFormat() {
        TenantContext ctx = new TenantContext("tenant-1", "user-1", "sid", "agent-1");
        assertEquals("tenant-1:user-1", ctx.userId());
    }

    @Test
    void testTenantContextFromHeaders() {
        TenantContext ctx = TenantContext.fromHeaders("t1", "u1", "sid", "agent-1");
        assertEquals("t1:u1", ctx.userId());
    }

    @Test
    void testTenantContextDefaultValues() {
        TenantContext ctx = TenantContext.fromHeaders(null, null, "sid", "agent-1");
        assertEquals("default:anonymous", ctx.userId());
    }

    @Test
    void testAgentPropertiesDefaults() {
        AgentProperties p = AgentProperties.defaults();
        assertEquals("rnd-assistant", p.name());
        assertEquals("openai:gpt-4o", p.model());
        assertTrue(p.planModeEnabled());
        assertFalse(p.allowShellInPlanMode());
    }

    @Test
    void testAgentPropertiesBuilder() {
        AgentProperties p = AgentProperties.builder()
                .name("test-agent")
                .model("dashscope:qwen-plus")
                .filesystem(FilesystemConfig.defaults())
                .sandbox(SandboxConfig.defaults())
                .build();
        assertEquals("test-agent", p.name());
        assertEquals("dashscope:qwen-plus", p.model());
    }

    @Test
    void testFilesystemConfigMode() {
        FilesystemConfig local = new FilesystemConfig("LOCAL", "USER", "_default");
        assertTrue(local.isLocal());
        assertFalse(local.isRemote());

        FilesystemConfig remote = new FilesystemConfig("REMOTE", "SESSION", "anon");
        assertTrue(remote.isRemote());
        assertFalse(remote.isSandbox());
    }

    @Test
    void testWorkbenchPropertiesDefaults() {
        WorkbenchProperties wp = WorkbenchProperties.defaults();
        assertEquals("rnd-assistant", wp.mainAgent());
        assertNotNull(wp.firstAgent());
        assertEquals("rnd-assistant", wp.firstAgent().name());
    }

    @Test
    void testSandboxConfigMemoryBytes() {
        SandboxConfig sc = new SandboxConfig("ubuntu:24.04", 512, 2, "", "LOCAL", true);
        assertEquals(512 * 1024 * 1024, sc.memoryBytes());
    }

    @Test
    void testAgentPropertiesResolveMemoryModel() {
        AgentProperties withCustomMemory = AgentProperties.builder()
                .model("openai:gpt-4o")
                .build();
        // memoryModel 为 null 时回退到主模型
        assertNotNull(withCustomMemory.resolveMemoryModel());
    }

    @Test
    void testAgentPropertiesLabel() {
        AgentProperties p = AgentProperties.builder()
                .name("rnd-assistant-pro")
                .model("deepseek:deepseek-v4-pro")
                .label("DeepSeek V4 Pro")
                .build();
        assertEquals("DeepSeek V4 Pro", p.label());
    }

    @Test
    void testListModelsDerivesFromAgents() {
        WorkbenchProperties wp = new WorkbenchProperties(
                "_default", "rnd-assistant-pro", "dev",
                new WorkbenchProperties.StoreConfig("json-file", ""),
                List.of(
                        AgentProperties.builder()
                                .name("rnd-assistant-pro")
                                .model("deepseek:deepseek-v4-pro")
                                .label("DeepSeek V4 Pro")
                                .build(),
                        AgentProperties.builder()
                                .name("rnd-assistant-flash")
                                .model("deepseek:deepseek-v4-flash")
                                .label("DeepSeek V4 Flash")
                                .build()));
        List<ModelInfo> models = wp.listModels();
        assertEquals(2, models.size());
        assertEquals("deepseek-v4-pro", models.get(0).id());
        assertEquals("DeepSeek V4 Pro", models.get(0).label());
        assertEquals("rnd-assistant-pro", models.get(0).agentId());
        assertEquals("deepseek-v4-flash", models.get(1).id());
        assertEquals("rnd-assistant-flash", models.get(1).agentId());
    }

    @Test
    void testListModelsLabelFallbackToModelId() {
        WorkbenchProperties wp = new WorkbenchProperties(
                "_default", "rnd-assistant", "dev",
                new WorkbenchProperties.StoreConfig("json-file", ""),
                List.of(AgentProperties.builder()
                        .name("rnd-assistant")
                        .model("deepseek:deepseek-chat")
                        .build()));
        List<ModelInfo> models = wp.listModels();
        assertEquals(1, models.size());
        // 未配置 label 时回退到模型 ID
        assertEquals("deepseek-chat", models.get(0).label());
    }

    @Test
    void testListModelsEmptyWhenNoAgents() {
        WorkbenchProperties wp = new WorkbenchProperties(
                "_default", "rnd-assistant", "dev",
                new WorkbenchProperties.StoreConfig("json-file", ""),
                List.of());
        assertTrue(wp.listModels().isEmpty());
    }
}