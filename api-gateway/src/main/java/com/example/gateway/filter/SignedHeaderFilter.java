package com.example.gateway.filter;

import com.example.gateway.util.JwtUtil;
import com.example.gateway.util.SignatureUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(-50)
public class SignedHeaderFilter implements WebFilter {
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private SignatureUtil signatureUtil;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/identity/register",
            "/api/identity/login",
            "/api/identity/refresh-token",
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    };

    private boolean isPublicEndpoint(String path) {
        for (String p : PUBLIC_ENDPOINTS) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String token = authHeader.substring(7);
        Claims claims = jwtUtil.validateAndParseClaims(token);
        Long userId = claims.get("userId", Long.class);
        String email = claims.getSubject();
        String role = claims.get("role", String.class);
        long timestamp = System.currentTimeMillis();
        String signature = signatureUtil.generateSignature(userId, email, role, timestamp);
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Email", email)
                .header("X-User-Role", role)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Internal-Signature", signature)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        return chain.filter(mutatedExchange);
    }
}
