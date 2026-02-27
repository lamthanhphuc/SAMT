package com.example.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 
 * Gateway Routes Configuration
 * 
 * Client → REST/JSON → API Gateway → JWT Validation → Microservices
 * 
 * Routing Strategy:
 * - Identity Service: REST API (authentication endpoints)
 * - User-Group Service: REST API (group management)
 * - Project Config Service: REST + gRPC (REST via Gateway, gRPC for inter-service)
 * - Sync Service, Analysis Service: REST API (planned)
 * 
 * Authentication Flow:
 * 1. Gateway validates JWT
 * 2. Extracts user info (userId, email, role)
 * 3. Injects headers (X-User-Id, X-User-Email, X-User-Role)
 * 4. Forwards to downstream service
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator strictRouteLocator(RouteLocatorBuilder builder,
                                          com.example.gateway.filter.JwtAuthenticationFilter jwtAuthenticationFilter,
                                          com.example.gateway.filter.SignedHeaderFilter signedHeaderFilter
                                          // Add other filters as beans if implemented: RateLimiterFilter, CircuitBreakerFilter, RetryFilter, TimeoutFilter, ForwardFilter
    ) {
        return builder.routes()
                // ============================================
                // IDENTITY SERVICE - REST API (STRICT ORDER, RATE LIMIT)
                // ============================================
                .route("identity-service", r -> r
                        .path("/api/identity/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                        )
                        .uri("http://identity-service:8081")
                )

                // ============================================
                // LOGIN ENDPOINT (STRICT ORDER, RATE LIMIT)
                // ============================================
                .route("login-endpoint", r -> r
                        .path("/login")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                        )
                        .uri("http://identity-service:8081")
                )

                // ============================================
                // REGISTER ENDPOINT (STRICT ORDER, RATE LIMIT)
                // ============================================
                .route("register-endpoint", r -> r
                        .path("/register")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                        )
                        .uri("http://identity-service:8081")
                )

                // ============================================
                // USER-GROUP SERVICE - REST API (STRICT ORDER)
                // ============================================
                .route("user-group-service", r -> r
                        .path("/api/groups/**", "/api/users/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(cb -> cb.setName("user-group-cb")
                                    .setFallbackUri("forward:/fallback/user-group")
                                    .setRouteId("user-group-service"))
                                .retry(retry -> retry.setRetries(3).setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
                        )
                        .uri("http://user-group-service:8082")
                )

                // ============================================
                // PROJECT CONFIG SERVICE - REST API (STRICT ORDER)
                // ============================================
                .route("project-config-service", r -> r
                        .path("/api/project-configs/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(cb -> cb.setName("project-config-cb")
                                    .setFallbackUri("forward:/fallback/project-config")
                                    .setRouteId("project-config-service"))
                                .retry(retry -> retry.setRetries(3).setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
                        )
                        .uri("http://project-config-service:8083")
                )

                // ============================================
                // SYNC SERVICE - REST API (STRICT ORDER)
                // ============================================
                .route("sync-service", r -> r
                        .path("/api/sync/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(cb -> cb.setName("sync-cb")
                                    .setFallbackUri("forward:/fallback/sync")
                                    .setRouteId("sync-service"))
                                .retry(retry -> retry.setRetries(3).setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
                        )
                        .uri("http://sync-service:8083")
                )

                // ============================================
                // ANALYSIS SERVICE - REST API (STRICT ORDER)
                // ============================================
                .route("analysis-service", r -> r
                        .path("/api/analysis/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(cb -> cb.setName("analysis-cb")
                                    .setFallbackUri("forward:/fallback/analysis")
                                    .setRouteId("analysis-service"))
                                .retry(retry -> retry.setRetries(3).setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
                        )
                        .uri("http://analysis-service:8084")
                )

                // ============================================
                // REPORT SERVICE - REST API (STRICT ORDER)
                // ============================================
                .route("report-service", r -> r
                        .path("/api/reports/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter, com.example.gateway.filter.JwtAuthenticationFilter.ORDER)
                                .filter(signedHeaderFilter, com.example.gateway.filter.SignedHeaderFilter.ORDER)
                                .requestRateLimiter(c -> c.setKeyResolverName("ipKeyResolver").setRateLimiterName("redisRateLimiter"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(cb -> cb.setName("report-cb")
                                    .setFallbackUri("forward:/fallback/report")
                                    .setRouteId("report-service"))
                                .retry(retry -> retry.setRetries(3).setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
                        )
                        .uri("http://report-service:8085")
                )

                .build();
    }
}
