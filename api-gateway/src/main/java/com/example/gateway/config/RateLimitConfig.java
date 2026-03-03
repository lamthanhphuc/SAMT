package com.example.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfig {

    private static final int LOGIN_WINDOW_SECONDS = 60;

    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                String first = xff.split(",")[0].trim();
                if (StringUtils.hasText(first)) {
                    return Mono.just(first);
                }
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }

            // Last-resort fallback: group unknown callers together rather than disabling rate limiting.
            return Mono.just("unknown");
        };
    }

    @Bean("globalRedisRateLimiter")
    public RedisRateLimiter globalRedisRateLimiter(
            @Value("${rate.limit.global.requests-per-second:100}") int requestsPerSecond,
            @Value("${rate.limit.global.burst-capacity:200}") int burstCapacity
    ) {
        if (requestsPerSecond <= 0 || burstCapacity <= 0) {
            throw new IllegalStateException("Global rate limit configuration must be positive.");
        }
        return new RedisRateLimiter(requestsPerSecond, burstCapacity, 1);
    }

    /**
     * Converts per-minute settings into RedisRateLimiter token-bucket values.
     *
     * Strategy:
     * - replenishRate = requestsPerMinute (tokens/second)
     * - requestedTokens = 60 (tokens/request)
     * - burstCapacity = burstRequestsPerMinute * 60 (max tokens)
     *
     * This yields an effective limit of ~requestsPerMinute per minute with a burst allowance.
     */
    @Bean("loginRedisRateLimiter")
    public RedisRateLimiter loginRedisRateLimiter(
            @Value("${rate.limit.login.requests-per-minute:5}") int requestsPerMinute,
            @Value("${rate.limit.login.burst-capacity:10}") int burstRequests
    ) {
        if (requestsPerMinute <= 0 || burstRequests <= 0) {
            throw new IllegalStateException("Login rate limit configuration must be positive.");
        }

        int replenishRate = requestsPerMinute;
        int requestedTokens = LOGIN_WINDOW_SECONDS;
        int burstCapacity = Math.multiplyExact(burstRequests, LOGIN_WINDOW_SECONDS);

        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }
}
