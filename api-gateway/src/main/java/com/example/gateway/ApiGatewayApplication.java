package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * API Gateway Application - Entry Point
 * 
 * Responsibilities:
 * - Route HTTP requests to backend microservices
 * - JWT authentication and authorization
 * - Rate limiting and security
 * - Centralized logging and monitoring
 * - Swagger API documentation aggregation
 * 
 * Technology Stack:
 * - Spring Cloud Gateway (Reactive)
 * - Spring Security (JWT validation)
 * - Redis (Rate limiting, session)
 * - Actuator (Health checks)
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * Fallback route configuration
     * Provides default routing behavior if routes config file is missing
     */
    @Bean
    public RouteLocator fallbackRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Identity Service
                .route("identity-service", r -> r
                        .path("/api/auth/**", "/api/users/**")
                        .uri("lb://identity-service"))
                
                // User-Group Service
                .route("user-group-service", r -> r
                        .path("/api/groups/**", "/api/semesters/**")
                        .uri("lb://user-group-service"))
                
                // Project Config Service
                .route("project-config-service", r -> r
                        .path("/api/project-configs/**")
                        .uri("lb://project-config-service"))
                
                // Sync Service
                .route("sync-service", r -> r
                        .path("/api/sync/**")
                        .uri("lb://sync-service"))
                
                // Analysis Service
                .route("analysis-service", r -> r
                        .path("/api/analysis/**")
                        .uri("lb://analysis-service"))
                
                // Report Service
                .route("report-service", r -> r
                        .path("/api/reports/**")
                        .uri("lb://report-service"))
                
                // Notification Service
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .uri("lb://notification-service"))
                
                // Actuator endpoints (public)
                .route("actuator", r -> r
                        .path("/actuator/**")
                        .uri("forward:/actuator"))
                
                .build();
    }
}
