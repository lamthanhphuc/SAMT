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
                                                                         @Value("${gateway.upstream.project-config:http://project-config-service:8084}") String projectConfigServiceUri,
                                                                         @Value("${gateway.upstream.sync:http://sync-service:8083}") String syncServiceUri,
                                                                         @Value("${gateway.upstream.analysis:http://analysis-service:8087}") String analysisServiceUri,
                                                                         @Value("${gateway.upstream.report:http://report-service:8088}") String reportServiceUri,
                                     @Value("${gateway.upstream.notification:http://notification-service:8085}") String notificationServiceUri
    ) {
        return builder.routes()

                .route("identity-jwks", r -> r
                        .path("/.well-known/jwks.json")
                        .uri(identityServiceUri))

                // ========================================
                //   OPENAPI DOCS PROXY ROUTES (no auth)
                // ========================================

                .route("identity-swagger", r -> r
                        .path("/identity/v3/api-docs/**", "/identity/v3/api-docs")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri(identityServiceUri))

                .route("user-group-swagger", r -> r
                        .path("/user-group/v3/api-docs/**", "/user-group/v3/api-docs")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri(userGroupServiceUri))

                .route("project-config-swagger", r -> r
                        .path("/project-config/v3/api-docs/**", "/project-config/v3/api-docs")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri(projectConfigServiceUri))

                .route("sync-swagger", r -> r
                        .path("/sync/v3/api-docs/**", "/sync/v3/api-docs")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri(syncServiceUri))

                .route("analysis-swagger", r -> r
                        .path("/analysis/v3/api-docs/**", "/analysis/v3/api-docs")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri(analysisServiceUri))

                .route("report-swagger", r -> r
                        .path("/report/v3/api-docs/**", "/report/v3/api-docs")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri(reportServiceUri))

                .route("notification-swagger", r -> r
                        .path("/notification/v3/api-docs/**", "/notification/v3/api-docs")
                        .filters(f -> f
                                .stripPrefix(1)
                                .preserveHostHeader())
                        .uri(notificationServiceUri))

                // ========================================
                //   API ROUTES
                // ========================================

                .route("identity-login", r -> r
                        .path("/api/identity/login")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.loginRateLimit("identity-login"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(identityServiceUri))

                .route("identity-auth-login", r -> r
                        .path("/api/auth/login")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("identity-auth-login"))
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(identityServiceUri))

                .route("identity-auth-api", r -> r
                        .path("/api/auth/**", "/api/admin/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("identity-auth-api"))
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(identityServiceUri))

                .route("identity-profile", r -> r
                        .path("/profile")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("identity-profile"))
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(identityServiceUri))

                .route("identity-users-me", r -> r
                        .path("/api/users/me")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("identity-users-me"))
                                .setPath("/profile")
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(identityServiceUri))

                .route("identity-integrations-api", r -> r
                        .path("/api/integrations/**", "/api/members/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("identity-integrations-api"))
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(identityServiceUri))

                .route("identity-service-api", r -> r
                        .path("/api/identity/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("identity-service"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("identityServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/identity")))
                        .uri(identityServiceUri))

                .route("user-group-service-api", r -> r
                        .path("/api/groups/**", "/api/users/**", "/api/semesters/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("user-group-service"))
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("userGroupServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/user-group")))
                        .uri(userGroupServiceUri))

                .route("project-config-service-api", r -> r
                        .path("/api/project-configs/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("project-config-service"))
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(projectConfigServiceUri))

                .route("sync-service-api", r -> r
                        .path("/api/sync/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("sync-service"))
                                .addRequestHeader("X-Forwarded-Host", "gateway"))
                        .uri(syncServiceUri))

                .route("analysis-service-api", r -> r
                        .path("/api/analysis/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("analysis-service"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("analysisServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/analysis")))
                        .uri(analysisServiceUri))

                .route("report-service-api", r -> r
                        .path("/api/reports/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("report-service"))
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("reportServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/report")))
                        .uri(reportServiceUri))

                .route("notification-service-api", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f
                                .filter(rateLimitGatewayFilter.globalRateLimit("notification-service"))
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(c -> c
                                        .setName("notificationServiceCircuitBreaker")
                                        .setFallbackUri("forward:/__gateway/fallback/notification")))
                        .uri(notificationServiceUri))

                .build();
    }
}
