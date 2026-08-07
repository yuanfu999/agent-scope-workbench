package com.agent.agentscopenew;

import com.agent.agentscopenew.agent.AgentFactory;
import com.agent.agentscopenew.config.AgentProperties;
import com.agent.agentscopenew.config.DistributedStoreProvider;
import com.agent.agentscopenew.config.FilesystemConfig;
import com.agent.agentscopenew.config.WorkbenchProperties;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5 生产化单元测试（FR-10.1~FR-10.3）。
 * <p>
 * 覆盖：StoreConfig/ObservabilityConfig 配置模型、DistributedStoreProvider
 * 类型分派与失败路径、AgentFactory 构建期校验（REMOTE 无分布式存储拒绝启动）。
 */
class M5ProductionConfigTest {

    /** 默认可观测性配置（关闭态）。 */
    private static final WorkbenchProperties.ObservabilityConfig OBS_DISABLED =
            new WorkbenchProperties.ObservabilityConfig(false, "http://localhost:4317", "agentscope-workbench");

    /** 默认 json-file 存储配置。 */
    private static final WorkbenchProperties.StoreConfig JSON_FILE_STORE =
            new WorkbenchProperties.StoreConfig("json-file", "", "agentscope:workbench");

    @BeforeAll
    static void registerMockModel() {
        // 构建期不连接模型，仅需可解析的 Model：注册 mock 避免依赖真实 API key
        Model mock = new Model() {
            @Override
            public Flux<io.agentscope.core.model.ChatResponse> stream(List<Msg> msgs,
                                                                      List<ToolSchema> tools,
                                                                      GenerateOptions options) {
                return Flux.empty();
            }

            @Override
            public String getModelName() {
                return "openai:gpt-4o";
            }
        };
        // 完整 ID 与 provider 前缀双注册，兼容精确匹配与前缀匹配
        ModelRegistry.register("openai:gpt-4o", mock);
        ModelRegistry.register("openai", mock);
    }

    @Test
    void testStoreConfigDefaults() {
        WorkbenchProperties wp = WorkbenchProperties.defaults();
        assertEquals("json-file", wp.store().type());
        assertEquals("", wp.store().redisUrl());
        assertEquals("agentscope:workbench", wp.store().keyPrefix());
    }

    @Test
    void testObservabilityConfigDefaults() {
        WorkbenchProperties wp = WorkbenchProperties.defaults();
        assertFalse(wp.observability().enabled());
        assertEquals("http://localhost:4317", wp.observability().otlpEndpoint());
        assertEquals("agentscope-workbench", wp.observability().serviceName());
    }

    @Test
    void testObservabilityConfigEnabled() {
        WorkbenchProperties.ObservabilityConfig obs =
                new WorkbenchProperties.ObservabilityConfig(true, "http://collector:4317", "my-service");
        assertTrue(obs.enabled());
        assertEquals("http://collector:4317", obs.otlpEndpoint());
        assertEquals("my-service", obs.serviceName());
    }

    @Test
    void testProviderJsonFileReturnsNull() {
        DistributedStoreProvider provider = new DistributedStoreProvider(JSON_FILE_STORE);
        assertNull(provider.get());
        assertNull(provider.getUnifiedJedis());
    }

    @Test
    void testProviderNullStoreReturnsNull() {
        assertNull(new DistributedStoreProvider(null).get());
    }

    @Test
    void testProviderUnknownTypeThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DistributedStoreProvider(
                        new WorkbenchProperties.StoreConfig("hbase", "", "p")));
        assertTrue(ex.getMessage().contains("hbase"));
        assertTrue(ex.getMessage().contains("json-file / redis"));
    }

    @Test
    void testProviderMySqlReservedThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DistributedStoreProvider(
                        new WorkbenchProperties.StoreConfig("mysql", "", "p")));
        assertTrue(ex.getMessage().contains("预留"));
    }

    @Test
    void testProviderRedisBlankUrlThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DistributedStoreProvider(
                        new WorkbenchProperties.StoreConfig("redis", "", "p")));
        assertTrue(ex.getMessage().contains("redis-url"));
    }

    @Test
    void testProviderRedisUnreachableThrows() {
        // 必然不可达端口：验证 Redis 不可达时启动即失败且报错含配置指引（FR-10.2 失败路径）
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DistributedStoreProvider(
                        new WorkbenchProperties.StoreConfig("redis", "redis://127.0.0.1:1", "p")));
        assertTrue(ex.getMessage().contains("127.0.0.1:1"));
        assertTrue(ex.getMessage().contains("Redis 服务状态"));
    }

    @Test
    void testAgentFactoryRemoteWithoutStoreThrows() {
        // FR-10.3 构建期校验：REMOTE 文件系统无分布式存储 → IllegalStateException
        WorkbenchProperties wp = new WorkbenchProperties(
                "_default", "rnd-assistant", "dev", JSON_FILE_STORE, OBS_DISABLED,
                List.of(AgentProperties.defaults()));
        AgentFactory factory = new AgentFactory(wp, new DistributedStoreProvider(wp.store()));
        AgentProperties remoteAgent = AgentProperties.builder()
                .name("remote-agent")
                .filesystem(new FilesystemConfig("REMOTE", "USER", "_default"))
                .subagents(List.of())
                .build();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.build(remoteAgent));
        assertTrue(ex.getMessage().contains("remote-agent"));
        assertTrue(ex.getMessage().contains("workbench.store.type=redis"));
    }

    @Test
    void testAgentFactoryLocalJsonFileNoChange() {
        // 验收口径 1：dev 默认（json-file + LOCAL）零变化，构建成功
        WorkbenchProperties wp = new WorkbenchProperties(
                "_default", "rnd-assistant", "dev", JSON_FILE_STORE, OBS_DISABLED,
                List.of(AgentProperties.defaults()));
        AgentFactory factory = new AgentFactory(wp, new DistributedStoreProvider(wp.store()));
        AgentProperties localAgent = AgentProperties.builder()
                .name("local-agent")
                .filesystem(new FilesystemConfig("LOCAL", "USER", "_default"))
                .subagents(List.of())
                .skillsRepositories(List.of())
                .build();
        HarnessAgent agent = factory.build(localAgent);
        assertNotNull(agent);
        assertEquals("local-agent", agent.getName());
    }
}
