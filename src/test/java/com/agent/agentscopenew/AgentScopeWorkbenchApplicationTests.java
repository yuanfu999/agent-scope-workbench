package com.agent.agentscopenew;

import com.agent.agentscopenew.config.AgentProperties;
import com.agent.agentscopenew.config.FilesystemConfig;
import com.agent.agentscopenew.config.SandboxConfig;
import com.agent.agentscopenew.config.WorkbenchProperties;
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
}