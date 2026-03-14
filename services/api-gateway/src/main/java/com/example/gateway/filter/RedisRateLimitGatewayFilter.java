package com.example.gateway.filter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class RedisRateLimitGatewayFilter {

    private final RedisRateLimiter globalLimiter;
    private final RedisRateLimiter loginLimiter;
    private final KeyResolver keyResolver;

    public RedisRateLimitGatewayFilter(
            @Qualifier("globalRedisRateLimiter") RedisRateLimiter globalLimiter,
            @Qualifier("loginRedisRateLimiter") RedisRateLimiter loginLimiter,
            @Qualifier("ipKeyResolver") KeyResolver keyResolver
    ) {
        this.globalLimiter = globalLimiter;
        this.loginLimiter = loginLimiter;
        this.keyResolver = keyResolver;
    }

    public GatewayFilter globalRateLimit(String routeId) {
        return rateLimit(routeId, globalLimiter);
    }

    public GatewayFilter loginRateLimit(String routeId) {
        return rateLimit(routeId, loginLimiter);
    }

    public GatewayFilter registerRateLimit(String routeId) {
        // No dedicated register limiter is configured yet; reuse the login limiter.
        return rateLimit(routeId, loginLimiter);
    }

    private GatewayFilter rateLimit(String routeId, RedisRateLimiter limiter) {
        String resolvedRouteId = StringUtils.hasText(routeId) ? routeId : "route";

        return (exchange, chain) -> keyResolver.resolve(exchange)
                .switchIfEmpty(Mono.just("unknown"))
                .flatMap(key -> limiter.isAllowed(resolvedRouteId, key)
                        .flatMap(response -> {
                            response.getHeaders().forEach((headerName, headerValue) ->
                                    exchange.getResponse().getHeaders().add(headerName, headerValue)
                            );
                            if (response.isAllowed()) {
                                return chain.filter(exchange);
                            }
                            return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS));
                        })
                        // Fail-open for Redis/unexpected limiter errors to avoid total outage.
                        .onErrorResume(ex -> ex instanceof ResponseStatusException
                                ? Mono.error(ex)
                                : chain.filter(exchange))
                );
    }
}
