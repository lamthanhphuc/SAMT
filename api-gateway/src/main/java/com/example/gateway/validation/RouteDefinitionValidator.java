package com.example.gateway.validation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RouteDefinitionValidator implements InitializingBean {

    private final RouteLocator routeLocator;

    // Documented strict order
    private static final List<String> REQUIRED_FILTER_ORDER = List.of(
        "CorsGatewayFilter", "RateLimiterGatewayFilter", "JwtGatewayFilter",
        "SignedHeaderGatewayFilter", "CircuitBreakerGatewayFilter",
        "RetryGatewayFilter", "TimeoutGatewayFilter", "ForwardGatewayFilter"
    );

    public RouteDefinitionValidator(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Override
    public void afterPropertiesSet() {
        routeLocator.getRoutes().collectList().block().forEach(route -> {
            List<String> filterNames = route.getFilters().stream()
                .filter(f -> f instanceof OrderedGatewayFilter)
                .sorted(Comparator.comparingInt(f -> ((OrderedGatewayFilter) f).getOrder()))
                .map(f -> f.getClass().getSimpleName())
                .collect(Collectors.toList());

            if (!filterNames.equals(REQUIRED_FILTER_ORDER)) {
                throw new IllegalStateException(
                    "Route " + route.getId() + " filter order mismatch. Expected: " +
                    REQUIRED_FILTER_ORDER + ", Found: " + filterNames
                );
            }
        });
    }
}
