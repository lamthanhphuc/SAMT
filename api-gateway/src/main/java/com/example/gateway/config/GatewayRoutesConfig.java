package com.example.gateway.config;

import com.example.gateway.filter.RedisRateLimitGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                                                         RedisRateLimitGatewayFilter rateLimitGatewayFilter,
                                                                         @Value("${gateway.upstream.identity:http://identity-service:8081}") String identityServiceUri,
                                                                         @Value("${gateway.upstream.user-group:http://user-group-service:8082}") String userGroupServiceUri,
                                                                         @Value("${gateway.upstream.project-config:http://project-config-service:8083}") String projectConfigServiceUri,
                                                                         @Value("${gateway.upstream.sync:http://sync-service:8084}") String syncServiceUri,
                                                                         @Value("${gateway.upstream.analysis:http://analysis-service:8085}") String analysisServiceUri,
                                                                         @Value("${gateway.upstream.report:http://report-service:8086}") String reportServiceUri
        ) {
        return builder.routes()
                .route("identity-login", r -> r
                        .path("/api/identity/login")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.loginRateLimit("identity-login"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("identityServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/identity")))
                        .uri(identityServiceUri))

                .route("identity-service", r -> r
                        .path("/api/identity/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("identity-service"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("identityServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/identity")))
                        .uri(identityServiceUri))

                .route("user-group-service", r -> r
                        .path("/api/groups/**", "/api/users/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("user-group-service"))
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("userGroupServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/user-group")))
                        .uri(userGroupServiceUri))

                .route("project-config-service", r -> r
                        .path("/api/project-configs/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("project-config-service"))
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("projectConfigServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/project-config")))
                        .uri(projectConfigServiceUri))

                .route("sync-service", r -> r
                        .path("/api/sync/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("sync-service"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("syncServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/sync")))
                        .uri(syncServiceUri))

                .route("analysis-service", r -> r
                        .path("/api/analysis/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("analysis-service"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("analysisServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/analysis")))
                        .uri(analysisServiceUri))

                .route("report-service", r -> r
                        .path("/api/reports/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("report-service"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("reportServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/report")))
                                                .uri(reportServiceUri))

                .build();
    }
}
