package com.example.gateway.filter;

import com.example.gateway.error.GatewayErrorResponseWriter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String ORIGINAL_REQUEST_PATH_ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".ORIGINAL_REQUEST_PATH";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String current = exchange.getRequest().getHeaders().getFirst(GatewayErrorResponseWriter.HEADER_NAME);
        String correlationId = GatewayErrorResponseWriter.resolveCorrelationId(current);
        String requestPath = exchange.getRequest().getURI().getPath();

        ServerHttpRequest request = exchange.getRequest();
        if (!StringUtils.hasText(current)) {
            request = request.mutate().header(GatewayErrorResponseWriter.HEADER_NAME, correlationId).build();
        }

        if (!requestPath.startsWith("/__gateway/fallback/")
            && !exchange.getAttributes().containsKey(ORIGINAL_REQUEST_PATH_ATTRIBUTE)) {
            exchange.getAttributes().put(ORIGINAL_REQUEST_PATH_ATTRIBUTE, requestPath);
        }

        exchange.getResponse().getHeaders().set(GatewayErrorResponseWriter.HEADER_NAME, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
    }
}