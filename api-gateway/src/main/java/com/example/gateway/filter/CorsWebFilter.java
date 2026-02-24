package com.example.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(CorsWebFilter.ORDER)
public class CorsWebFilter implements WebFilter, Ordered {
    public static final int ORDER = 0; // Strict deterministic order for startup validation
    // Strict CORS whitelist from config
    private final List<String> allowedOrigins;
    public CorsWebFilter() {
        String origins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (origins == null || origins.isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS environment variable must be set for strict CORS enforcement");
        }
        allowedOrigins = List.of(origins.split(","));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin != null && allowedOrigins.contains(origin)) {
            exchange.getResponse().getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            exchange.getResponse().getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            exchange.getResponse().getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization,X-User-Id,X-User-Role");
            exchange.getResponse().getHeaders().add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Authorization,X-User-Id,X-User-Role");
            exchange.getResponse().getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "false");
        }
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
