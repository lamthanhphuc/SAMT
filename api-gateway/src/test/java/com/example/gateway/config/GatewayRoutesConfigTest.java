package com.example.gateway.config;

import com.example.gateway.filter.RedisRateLimitGatewayFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRoutesConfigTest {

    private List<Route> routes;

    @BeforeEach
    void setUp() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        RouteLocatorBuilder builder = new RouteLocatorBuilder(context);

        RedisRateLimitGatewayFilter rateLimitGatewayFilter = mock(RedisRateLimitGatewayFilter.class);
        GatewayFilter noOpFilter = (exchange, chain) -> chain.filter(exchange);
        when(rateLimitGatewayFilter.loginRateLimit(anyString())).thenReturn(noOpFilter);
        when(rateLimitGatewayFilter.globalRateLimit(anyString())).thenReturn(noOpFilter);

        RouteLocator routeLocator = new GatewayRoutesConfig().routeLocator(
            builder,
            rateLimitGatewayFilter,
            "http://identity.example",
            "http://user-group.example",
            "http://project-config.example",
            "http://sync.example",
            "http://analysis.example",
            "http://report.example",
            "http://notification.example"
        );
        routes = routeLocator.getRoutes().collectList().block();
    }

    @Test
    void matchesIdentityProfileRequestsToIdentityRoute() {
        Route route = match("/api/identity/profile");

        assertThat(route.getId()).isEqualTo("identity-service-api");
        assertThat(route.getUri().toString()).isEqualTo("http://identity.example");
    }

    @Test
    void matchesUserGroupRequestsToUserGroupRoute() {
        Route route = match("/api/groups/42");

        assertThat(route.getId()).isEqualTo("user-group-service-api");
        assertThat(route.getUri().toString()).isEqualTo("http://user-group.example");
    }

    @Test
    void matchesSwaggerRequestsToServiceSpecificSwaggerRoute() {
        Route route = match("/project-config/v3/api-docs");

        assertThat(route.getId()).isEqualTo("project-config-swagger");
        assertThat(route.getUri().toString()).isEqualTo("http://project-config.example");
    }

    private Route match(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return routes.stream()
            .filter(route -> route.getPredicate().apply(exchange))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No route matched path " + path));
    }
}