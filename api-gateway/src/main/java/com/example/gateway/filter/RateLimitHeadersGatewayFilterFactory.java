package com.example.gateway.filter;

import com.example.gateway.service.IpResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter to add rate limit response headers for client visibility.
 * Uses shared IpResolutionService for consistent IP resolution with rate limiting.
 */
@Slf4j
@Component 
@RequiredArgsConstructor
public class RateLimitHeadersGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimitHeadersGatewayFilterFactory.Config> {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final IpResolutionService ipResolutionService;

    public RateLimitHeadersGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> {
                        addRateLimitHeaders(exchange, config);
                    }));
        };
    }

    private void addRateLimitHeaders(ServerWebExchange exchange, Config config) {
        try {
            // Use shared IP resolution service for consistency
            String clientIp = ipResolutionService.resolveClientIp(exchange.getRequest());
            String rateLimitKey = "request_rate_limiter." + clientIp;
            
            HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
            
            // Add standard rate limit headers
            responseHeaders.add("X-RateLimit-Limit", String.valueOf(config.getLimit()));
            
            // Try to get current count from Redis (non-blocking)
            redisTemplate.opsForValue()
                    .get(rateLimitKey)
                    .defaultIfEmpty("0")
                    .subscribe(
                        currentCount -> {
                            try {
                                int count = Integer.parseInt(currentCount);
                                int remaining = Math.max(0, config.getLimit() - count);
                                responseHeaders.set("X-RateLimit-Remaining", String.valueOf(remaining));
                                
                                // Add reset time (current window + 1 minute)
                                long resetTime = System.currentTimeMillis() / 1000 + 60; // 1 minute window
                                responseHeaders.set("X-RateLimit-Reset", String.valueOf(resetTime));
                                
                                log.debug("Added rate limit headers: limit={}, remaining={}, reset={} for IP={}", 
                                         config.getLimit(), remaining, resetTime, clientIp);
                            } catch (NumberFormatException e) {
                                // Fallback headers if Redis data is invalid
                                responseHeaders.set("X-RateLimit-Remaining", String.valueOf(config.getLimit()));
                                responseHeaders.set("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + 60));
                            }
                        },
                        error -> {
                            // Fallback headers if Redis is unavailable
                            responseHeaders.set("X-RateLimit-Remaining", String.valueOf(config.getLimit()));
                            responseHeaders.set("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + 60));
                            log.debug("Could not retrieve rate limit count from Redis, using fallback headers");
                        }
                    );
                    
        } catch (Exception e) {
            log.debug("Failed to add rate limit headers: {}", e.getMessage());
            // Don't fail the request if headers can't be added
        }
    }

    public static class Config {
        private int limit = 100; // Default rate limit

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}