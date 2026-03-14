package com.example.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Trust boundary: strip any caller-supplied internal identity headers.
 * The gateway is the only component allowed to inject these headers.
 */
@Component
public class HeaderSanitizationFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    // Never trust caller-supplied internal identity headers.
                    // NOTE: Do NOT remove Authorization here; the gateway must read the external JWT first.
                    // InternalJwtWebFilter will replace Authorization with the internal JWT before proxying.
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                    headers.remove("X-Internal-Timestamp");
                    headers.remove("X-Internal-Signature");
                    headers.remove("X-Internal-Key-Id");
                    headers.remove("X-Internal-Original-Path");
                })
                .build();

        return chain.filter(exchange.mutate().request(sanitized).build());
    }
}
