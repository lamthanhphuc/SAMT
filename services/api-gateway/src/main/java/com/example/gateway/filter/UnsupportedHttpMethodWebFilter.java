package com.example.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

@Component
public class UnsupportedHttpMethodWebFilter implements WebFilter, Ordered {

    private static final Set<String> UNSUPPORTED_METHODS = Set.of("TRACE", "QUERY");
    private static final String ALLOW_HEADER = "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String method = exchange.getRequest().getMethod() == null
                ? ""
                : exchange.getRequest().getMethod().name();
        if (!UNSUPPORTED_METHODS.contains(method)) {
            return chain.filter(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
        response.getHeaders().set(HttpHeaders.ALLOW, ALLOW_HEADER);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"statusCode\":405,\"error\":\"Method Not Allowed\",\"message\":\"Method not allowed\",\"timestamp\":\"%s\"}",
                Instant.now()
        );

        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
        ));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}