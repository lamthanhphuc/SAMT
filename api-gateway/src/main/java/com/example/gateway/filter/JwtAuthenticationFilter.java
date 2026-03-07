package com.example.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Component  // ENABLED FOR HEADER SPOOFING PREVENTION
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    public static final String AUTHENTICATED_MARKER_ATTRIBUTE = "gateway.authenticated";
    public static final String AUTHENTICATED_JWT_ATTRIBUTE = "gateway.authenticatedJwt";

    private final ReactiveJwtDecoder jwtDecoder;
    private final boolean prodProfile;

    public JwtAuthenticationFilter(ReactiveJwtDecoder jwtDecoder, Environment environment) {
        this.jwtDecoder = jwtDecoder;
        this.prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
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

        return jwtDecoder.decode(token)
                .flatMap(jwt -> authenticateAndContinue(exchange, chain, jwt))
                .onErrorResume(ex -> unauthorized(exchange));
    }

    private Mono<Void> authenticateAndContinue(ServerWebExchange exchange, WebFilterChain chain, Jwt jwt) {
        if (!StringUtils.hasText(jwt.getSubject())) {
            return unauthorized(exchange);
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty() || roles.stream().noneMatch(StringUtils::hasText)) {
            return unauthorized(exchange);
        }

        if (!StringUtils.hasText(jwt.getId()) || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
            return unauthorized(exchange);
        }

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .filter(StringUtils::hasText)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                jwt.getSubject(),
                null,
                authorities
        );

        exchange.getAttributes().put(AUTHENTICATED_MARKER_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(AUTHENTICATED_JWT_ATTRIBUTE, jwt);

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private String resolveBearerToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(auth) || !auth.startsWith("Bearer ")) {
            return null;
        }
        return auth.substring(7).trim();
    }

    private boolean isPublicEndpoint(String path) {
        if ("/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/actuator/health".equals(path)) {
            return true;
        }

        if (path.startsWith("/identity/v3/api-docs")
                || path.startsWith("/user-group/v3/api-docs")
                || path.startsWith("/project-config/v3/api-docs")
                || path.startsWith("/sync/v3/api-docs")
                || path.startsWith("/analysis/v3/api-docs")
                || path.startsWith("/report/v3/api-docs")
                || path.startsWith("/notification/v3/api-docs")
                || "/swagger-ui.html".equals(path)
                || path.startsWith("/swagger-ui/")) {
            return true;
        }

        if ("/.well-known/internal-jwks.json".equals(path)
                || "/.well-known/jwks.json".equals(path)) {
            return true;
        }

        return false;
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
            return "{\"statusCode\":401,\"error\":\"Unauthorized\",\"message\":\"Unauthorized\",\"timestamp\":\""
                    + Instant.now().toString()
                    + "\"}";
        }
    }
}
