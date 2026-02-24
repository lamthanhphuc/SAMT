package com.example.gateway.resolver;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IpKeyResolver implements KeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        try {
            return Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress());
        } catch (Exception e) {
            // Redis fail-open: allow request, log warning
            System.err.println("[WARN] Redis unavailable for rate limiting, allowing request: " + e.getMessage());
            return Mono.just("fail-open");
        }
    }
}
