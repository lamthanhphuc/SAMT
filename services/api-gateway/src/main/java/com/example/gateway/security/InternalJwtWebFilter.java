package com.example.gateway.security;

import com.example.gateway.filter.JwtAuthenticationFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * After external JWT is verified by JwtAuthenticationFilter, mint a short-lived internal JWT and forward it
 * downstream via Authorization: Bearer <internal-jwt>.
 */
@Component
public class InternalJwtWebFilter implements WebFilter, Ordered {

    private final InternalJwtIssuer issuer;

    public InternalJwtWebFilter(InternalJwtIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    public int getOrder() {
        // Run after HeaderSanitizationFilter (HIGHEST_PRECEDENCE), after JwtAuthenticationFilter
        // but before routing.
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (shouldPreserveExternalToken(exchange)) {
            return chain.filter(exchange);
        }

        Object authenticatedMarker = exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_MARKER_ATTRIBUTE);
        Object jwtAttribute = exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_JWT_ATTRIBUTE);

        if (!(authenticatedMarker instanceof Boolean isAuthenticated)
                || !isAuthenticated
                || !(jwtAttribute instanceof Jwt externalJwt)) {
            return chain.filter(exchange);
        }

        String sub = externalJwt.getSubject();
        if (!StringUtils.hasText(sub)) {
            return chain.filter(exchange);
        }

        String internalJwt = issuer.issueFromExternalJwt(externalJwt);

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        // Replace any inbound Authorization with our internal token.
        requestBuilder.headers(headers -> {
            headers.remove(HttpHeaders.AUTHORIZATION);
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + internalJwt);
        });

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private boolean shouldPreserveExternalToken(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return "/profile".equals(path)
            || "/api/users/me".equals(path)
                || path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/")
                || path.startsWith("/api/identity/");
    }
}
