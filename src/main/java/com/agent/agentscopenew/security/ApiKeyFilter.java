package com.agent.agentscopenew.security;

import com.agent.agentscopenew.config.WorkbenchProperties;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * API Key 鉴权过滤器（平台级）。
 * <p>
 * 所有 {@code /api/v1/**} 路径的请求需要携带 {@code X-API-Key} 请求头，
 * 值与配置 {@code workbench.api-key} 匹配。管理 API 路径 {@code /api/v1/admin/**}
 * 需要额外的管理员 Key（当前复用平台 Key）。
 * <p>
 * 演示/开发模式下可通过配置 {@code workbench.api-key=_default} 放行。
 */
@Slf4j
public class ApiKeyFilter implements WebFilter {

    private final String expectedApiKey;

    private static final String API_KEY_HEADER = "X-API-Key";

    /** 无需鉴权的路径前缀。 */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/",
            "/index.html",
            "/static/"
    };

    public ApiKeyFilter(WorkbenchProperties properties) {
        this.expectedApiKey = properties.apiKey();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 公开路径放行
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath) || path.startsWith(publicPath)) {
                return chain.filter(exchange);
            }
        }

        // 仅拦截 /api/v1/ 路径
        if (!path.startsWith("/api/v1/")) {
            return chain.filter(exchange);
        }

        // 默认 Key 为 _default 时跳过鉴权（开发模式）
        if ("_default".equals(expectedApiKey)) {
            return chain.filter(exchange);
        }

        String providedKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        if (providedKey == null || !providedKey.equals(expectedApiKey)) {
            log.warn("API Key 校验失败: path={}, providedKey={}", path,
                    providedKey != null ? "***" : "null");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}