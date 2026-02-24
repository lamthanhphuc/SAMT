package com.example.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class RouteFilterOrderIntegrationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void allRoutesHaveStrictFilterOrder() {
        List<String> expectedOrder = List.of(
            "CorsGatewayFilter", "RateLimiterGatewayFilter", "JwtGatewayFilter",
            "SignedHeaderGatewayFilter", "CircuitBreakerGatewayFilter",
            "RetryGatewayFilter", "TimeoutGatewayFilter", "ForwardGatewayFilter"
        );

        routeLocator.getRoutes().collectList().block().forEach(route -> {
            List<String> filterNames = route.getFilters().stream()
                .filter(f -> f instanceof org.springframework.cloud.gateway.filter.OrderedGatewayFilter)
                .sorted(Comparator.comparingInt(f -> ((org.springframework.cloud.gateway.filter.OrderedGatewayFilter) f).getOrder()))
                .map(f -> f.getClass().getSimpleName())
                .collect(Collectors.toList());
            assertThat(filterNames).as("Route " + route.getId() + " filter order").isEqualTo(expectedOrder);
        });
    }
}
