package com.example.gateway.security;

import com.example.gateway.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalJwtWebFilterTest {

    @Test
    void replacesAuthorizationHeaderForProtectedRoutes() {
        InternalJwtIssuer issuer = mock(InternalJwtIssuer.class);
        when(issuer.issueFromExternalJwt(jwt())).thenReturn("internal-jwt");
        InternalJwtWebFilter filter = new InternalJwtWebFilter(issuer);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/groups/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer external-jwt")
                .build()
        );
        exchange.getAttributes().put(JwtAuthenticationFilter.AUTHENTICATED_MARKER_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(JwtAuthenticationFilter.AUTHENTICATED_JWT_ATTRIBUTE, jwt());

        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        WebFilterChain chain = current -> {
            forwardedRequest.set(current.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwardedRequest.get().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer internal-jwt");
        verify(issuer).issueFromExternalJwt(jwt());
    }

    @Test
    void preservesExternalAuthorizationHeaderForIdentityRoutes() {
        InternalJwtIssuer issuer = mock(InternalJwtIssuer.class);
        InternalJwtWebFilter filter = new InternalJwtWebFilter(issuer);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/identity/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer external-jwt")
                .build()
        );
        exchange.getAttributes().put(JwtAuthenticationFilter.AUTHENTICATED_MARKER_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(JwtAuthenticationFilter.AUTHENTICATED_JWT_ATTRIBUTE, jwt());

        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        WebFilterChain chain = current -> {
            forwardedRequest.set(current.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwardedRequest.get().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer external-jwt");
        verify(issuer, never()).issueFromExternalJwt(jwt());
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("external-jwt")
            .header("alg", "RS256")
            .claim("roles", List.of("ADMIN"))
            .id("jwt-id")
            .subject("42")
            .issuedAt(Instant.parse("2026-03-11T10:00:00Z"))
            .expiresAt(Instant.parse("2026-03-11T10:15:00Z"))
            .build();
    }
}