package com.example.gateway.filter;

import com.example.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component  // ENABLED FOR HEADER SPOOFING PREVENTION
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    public static final String AUTHENTICATED_MARKER_ATTRIBUTE = "gateway.authenticated";
    public static final String AUTHENTICATED_CLAIMS_ATTRIBUTE = "gateway.authenticatedClaims";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String token = resolveBearerToken(exchange.getRequest());
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange);
        }

        final Claims claims;
        try {
            claims = jwtUtil.validateAndParseClaims(token);
        } catch (IllegalArgumentException ignored) {
            return unauthorized(exchange);
        }

        String userId = readUserId(claims);
        String subject = claims.getSubject();
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        if (!StringUtils.hasText(email)) {
            email = subject;
        }

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(subject) || !StringUtils.hasText(role) || !StringUtils.hasText(email)) {
            return unauthorized(exchange);
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                subject,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        // Set authentication attributes for SignedHeaderFilter to use
        exchange.getAttributes().put(AUTHENTICATED_MARKER_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(AUTHENTICATED_CLAIMS_ATTRIBUTE, claims);

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private String readUserId(Claims claims) {
        Object raw = claims.get("userId");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(raw);
    }

    private String resolveBearerToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(auth) || !auth.startsWith("Bearer ")) {
            return null;
        }
        return auth.substring(7).trim();
    }

    private boolean isPublicEndpoint(String path) {
        return "/api/identity/register".equals(path)
                || "/api/identity/login".equals(path)
                || "/api/identity/refresh-token".equals(path)
                || path.startsWith("/api/public/")
                || path.startsWith("/actuator/")
                || "/actuator".equals(path)
                || path.startsWith("/swagger-ui/")
                || "/swagger-ui.html".equals(path)
                || path.startsWith("/v3/api-docs/")
                || "/v3/api-docs".equals(path);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = JsonErrorBodies.unauthorized();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private static final class JsonErrorBodies {
        private JsonErrorBodies() {
        }

        private static String unauthorized() {
            return "{\"error\":{\"code\":401,\"message\":\"Unauthorized\"},\"timestamp\":\""
                    + Instant.now().toString()
                    + "\"}";
        }
    }
}
