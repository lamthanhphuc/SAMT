package com.example.gateway.config;

import com.example.gateway.filter.*;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder,
                                          CorsGatewayFilter cors,
                                          RateLimiterGatewayFilter rateLimiter,
                                          JwtGatewayFilter jwt,
                                          SignedHeaderGatewayFilter signedHeader,
                                          CircuitBreakerGatewayFilter circuitBreaker,
                                          RetryGatewayFilter retry,
                                          TimeoutGatewayFilter timeout,
                                          ForwardGatewayFilter forward) {
        return builder.routes()
            .route("identity-login", r -> r.path("/api/identity/login")
                .filters(f -> f
                    .filter(OrderedFilters.cors(cors))
                    .filter(OrderedFilters.rateLimiter(rateLimiter))
                    .filter(OrderedFilters.jwt(jwt))
                    .filter(OrderedFilters.signedHeader(signedHeader))
                    .filter(OrderedFilters.circuitBreaker(circuitBreaker))
                    .filter(OrderedFilters.retry(retry))
                    .filter(OrderedFilters.timeout(timeout))
                    .filter(OrderedFilters.forward(forward))
                )
                .uri("lb://identity-service"))
            // Repeat for /register, /refresh-token, and all other documented endpoints
            .build();
    }
}
