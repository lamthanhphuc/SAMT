package com.example.gateway.filter;

import com.example.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT Authentication Filter cho API Gateway
 * 
 * Xử lý:
 * 1. Extract JWT từ Authorization header
 * 2. Validate JWT signature và expiration
 * 3. Extract user info (userId, email, role)
 * 4. Inject vào gRPC metadata cho downstream services
 * 5. Set SecurityContext cho Spring Security
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(JwtAuthenticationFilter.ORDER)
public class JwtAuthenticationFilter implements WebFilter, org.springframework.core.Ordered {
    public static final int ORDER = 1; // Strict deterministic order for startup validation

    private final JwtUtil jwtUtil;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip JWT validation for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            // Validate JWT and extract claims
            Claims claims = jwtUtil.validateAndParseClaims(token);

            // Enforce token_type claim strictly
            String tokenType = claims.get("token_type", String.class);
            if (tokenType == null || !tokenType.equals("access")) {
                log.warn("JWT token_type claim missing or invalid for path: {}", path);
                return unauthorizedResponse(exchange, "Invalid token_type claim");
            }

            Long userId = claims.get("userId", Long.class);
            String email = claims.getSubject();
            String role = claims.get("role", String.class);

            log.debug("JWT validated for user: {} (userId={}, role={})", email, userId, role);

            // Inject user info into request headers for downstream microservices
            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(builder -> builder
                            .header("X-User-Id", String.valueOf(userId))
                            .header("X-User-Email", email)
                            .header("X-User-Role", role)
                    )
                    .build();

            // Set Spring Security context
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );

            return chain.filter(mutatedExchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return unauthorizedResponse(exchange, "JWT validation failed");
        }
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = String.format("{\"error\":{\"code\":401,\"message\":\"%s\"},\"timestamp\":\"%s\"}",
                message,
                java.time.OffsetDateTime.now().toString()
        );
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/identity/register") ||
               path.startsWith("/api/identity/login") ||
               path.startsWith("/api/identity/refresh-token") ||
               path.startsWith("/actuator") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/webjars");
    }
}
