package com.agent.agentscopenew.health;

import com.agent.agentscopenew.config.DistributedStoreProvider;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import redis.clients.jedis.UnifiedJedis;

/**
 * 存储健康指示器（FR-10.6）。
 * <p>
 * json-file（本地存储）→ UP；redis → ping 连通性探测（响应 OK 才 UP），
 * 不可达 → DOWN 并附原因。探测超时由 UnifiedJedis 客户端默认连接超时兜底。
 */
@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final DistributedStoreProvider storeProvider;

    public StorageHealthIndicator(DistributedStoreProvider storeProvider) {
        this.storeProvider = storeProvider;
    }

    @Override
    public Health health() {
        UnifiedJedis jedis = storeProvider.getUnifiedJedis();
        if (jedis == null) {
            return Health.up().withDetail("type", "json-file").build();
        }
        try {
            String pong = jedis.ping();
            return Health.up()
                    .withDetail("type", "redis")
                    .withDetail("pong", pong)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("type", "redis")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
