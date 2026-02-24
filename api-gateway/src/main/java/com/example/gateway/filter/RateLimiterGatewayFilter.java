package com.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;

@Component
public class RateLimiterGatewayFilter implements GatewayFilter {
    // Inject per-endpoint config as needed
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Implement per-endpoint rate limiting logic here
        // ...
        return chain.filter(exchange);
    }
}
