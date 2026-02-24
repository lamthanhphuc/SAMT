package com.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimiterGatewayFilter {

    public GatewayFilter apply() {

        return new OrderedGatewayFilter(
                (ServerWebExchange exchange, GatewayFilterChain chain) -> {

                    // TODO: rate limit logic here

                    return chain.filter(exchange);

                },
                OrderedFilters.RATE_LIMIT
        );
    }
}