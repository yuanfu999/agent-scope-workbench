package com.agent.agentscopenew.config;

import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.DistributedStore;

import lombok.extern.slf4j.Slf4j;

import redis.clients.jedis.UnifiedJedis;

/**
 * 分布式存储提供方（FR-10.1/FR-10.2）。
 * <p>
 * 根据 {@code workbench.store.type} 构建 {@link DistributedStore}：
 * <ul>
 *   <li>{@code json-file}（dev 默认）：不注入分布式存储，返回 null；</li>
 *   <li>{@code redis}（prod）：{@code new UnifiedJedis(redisUrl)} + 
 *       {@link RedisDistributedStore#fromJedis(UnifiedJedis, String)}，一键注入
 *       AgentStateStore / BaseStore / RedisSnapshotSpec / RedisSandboxExecutionGuard；</li>
 *   <li>{@code mysql}：预留，暂未支持；</li>
 *   <li>未知类型：抛 {@link IllegalStateException}。</li>
 * </ul>
 * Redis 模式在构造期执行一次 ping 连通性验证，不可达时启动即失败并给出配置指引。
 */
@Slf4j
public final class DistributedStoreProvider {

    private final DistributedStore distributedStore;
    private final UnifiedJedis jedis;

    /**
     * 根据存储配置构建分布式存储提供方。
     *
     * @param store 存储配置（可为 null，视为 json-file）
     * @throws IllegalStateException Redis 不可达 / 配置缺失 / 类型未知
     */
    public DistributedStoreProvider(WorkbenchProperties.StoreConfig store) {
        if (store == null || store.type() == null || store.type().isBlank()
                || "json-file".equalsIgnoreCase(store.type())) {
            this.distributedStore = null;
            this.jedis = null;
            log.info("分布式存储: type=json-file（本地文件存储，未注入 DistributedStore）");
            return;
        }
        switch (store.type().toLowerCase()) {
            case "redis":
                RedisConnection connection = buildRedisStore(store);
                this.distributedStore = connection.store();
                this.jedis = connection.client();
                break;
            case "mysql":
                throw new IllegalStateException(
                        "workbench.store.type=mysql 为预留类型，暂未支持，请改用 redis 或 json-file");
            default:
                throw new IllegalStateException(
                        "未知 workbench.store.type [" + store.type() + "]，支持: json-file / redis");
        }
    }

    /**
     * 获取分布式存储实例（json-file 模式返回 null）。
     */
    public DistributedStore get() {
        return distributedStore;
    }

    /**
     * 获取 UnifiedJedis 客户端（json-file 模式返回 null），供健康检查复用（FR-10.6）。
     */
    public UnifiedJedis getUnifiedJedis() {
        return jedis;
    }

    /**
     * 构建 Redis 分布式存储并验证连通性。
     */
    private RedisConnection buildRedisStore(WorkbenchProperties.StoreConfig store) {
        String redisUrl = store.redisUrl();
        if (redisUrl == null || redisUrl.isBlank()) {
            throw new IllegalStateException(
                    "workbench.store.type=redis 时必须配置 workbench.store.redis-url（如 redis://localhost:6379）");
        }
        UnifiedJedis client = new UnifiedJedis(redisUrl);
        try {
            client.ping();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Redis 不可达（" + redisUrl + "）：" + e.getMessage()
                            + "。请检查 workbench.store.redis-url 与 Redis 服务状态", e);
        }
        DistributedStore storeInstance = RedisDistributedStore.fromJedis(client, store.keyPrefix());
        log.info("分布式存储已就绪: type=redis, url={}, keyPrefix={}",
                maskRedisUrl(redisUrl), store.keyPrefix());
        return new RedisConnection(storeInstance, client);
    }

    /**
     * Redis 存储与客户端连接对。
     */
    private record RedisConnection(DistributedStore store, UnifiedJedis client) {
    }

    /**
     * 脱敏 Redis URL：仅展示 host:port，隐藏鉴权信息。
     */
    private String maskRedisUrl(String redisUrl) {
        int schemeEnd = redisUrl.indexOf("://");
        int hostStart = schemeEnd >= 0 ? schemeEnd + 3 : 0;
        int atIndex = redisUrl.indexOf('@');
        int hostStart2 = atIndex >= 0 ? atIndex + 1 : hostStart;
        String hostPort = redisUrl.substring(hostStart2);
        int slash = hostPort.indexOf('/');
        return slash >= 0 ? hostPort.substring(0, slash) : hostPort;
    }
}
