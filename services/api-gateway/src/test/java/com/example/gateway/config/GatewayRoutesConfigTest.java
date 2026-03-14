package com.example.gateway.config;

import com.example.gateway.filter.RedisRateLimitGatewayFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = GatewayRoutesConfigTest.GatewayRoutesTestConfig.class,
    properties = {
        "gateway.upstream.identity=http://identity.example",
        "gateway.upstream.user-group=http://user-group.example",
        "gateway.upstream.project-config=http://project-config.example",
        "gateway.upstream.sync=http://sync.example",
        "gateway.upstream.analysis=http://analysis.example",
        "gateway.upstream.report=http://report.example",
        "gateway.upstream.notification=http://notification.example"
    }
)
class GatewayRoutesConfigTest {

    @TestConfiguration
    @EnableAutoConfiguration
    @Import(GatewayRoutesConfig.class)
    static class GatewayRoutesTestConfig {
    }

    @Autowired
    private RouteLocator routeLocator;

    @MockBean
    private RedisRateLimitGatewayFilter rateLimitGatewayFilter;

    private List<Route> routes;

    @BeforeEach
    void setUp() {
        GatewayFilter noOpFilter = (exchange, chain) -> chain.filter(exchange);
        when(rateLimitGatewayFilter.loginRateLimit(anyString())).thenReturn(noOpFilter);
        when(rateLimitGatewayFilter.globalRateLimit(anyString())).thenReturn(noOpFilter);
        routes = routeLocator.getRoutes().collectList().block();
    }

    @Test
    void matchesIdentityProfileRequestsToIdentityRoute() {
        Route route = match("/api/identity/profile");

        assertThat(route.getId()).isEqualTo("identity-service-api");
        assertRouteUri(route.getUri(), "identity.example");
    }

    @Test
    void matchesUserGroupRequestsToUserGroupRoute() {
        Route route = match("/api/groups/42");

        assertThat(route.getId()).isEqualTo("user-group-service-api");
        assertRouteUri(route.getUri(), "user-group.example");
    }

    @Test
    void matchesSwaggerRequestsToServiceSpecificSwaggerRoute() {
        Route route = match("/project-config/v3/api-docs");

        assertThat(route.getId()).isEqualTo("project-config-swagger");
        assertRouteUri(route.getUri(), "project-config.example");
    }

    private void assertRouteUri(URI uri, String expectedHost) {
        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getHost()).isEqualTo(expectedHost);
    }

    private Route match(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return routes.stream()
            .filter(route -> Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No route matched path " + path));
    }
}