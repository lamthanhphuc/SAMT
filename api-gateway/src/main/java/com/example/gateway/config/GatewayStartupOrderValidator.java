package com.example.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Enforces strict deterministic filter order at startup.
 * Fails fast if any route's filter order does not match the documented sequence.
 */
@Component
public class GatewayStartupOrderValidator implements ApplicationRunner {
    @Autowired
    private RouteLocator routeLocator;

    // The expected filter class simple names in strict order
    private static final List<String> EXPECTED_ORDER = Arrays.asList(
            "JwtAuthenticationFilter",
            "SignedHeaderFilter"
            // Add more filter class names here if needed, in order
    );

    @Override
    public void run(ApplicationArguments args) {
        routeLocator.getRoutes().collectList().block().forEach(route -> {
            List<String> actualOrder = route.getFilters().stream()
                    .map(f -> f.getClass().getSimpleName())
                    .filter(EXPECTED_ORDER::contains)
                    .toList();
            if (!actualOrder.equals(EXPECTED_ORDER)) {
                throw new IllegalStateException("Startup failed: Filter order mismatch for route '" + route.getId() +
                        "'. Expected order: " + EXPECTED_ORDER + ", but found: " + actualOrder);
            }
        });
    }
}
