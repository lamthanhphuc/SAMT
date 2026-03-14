package com.example.gateway.filter;

import com.example.gateway.error.GatewayErrorResponseWriter;
import com.example.gateway.security.ExchangeAttributeSecurityContextRepository;
import com.example.gateway.security.PublicEndpointPaths;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component  // ENABLED FOR HEADER SPOOFING PREVENTION
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    public static final String AUTHENTICATED_MARKER_ATTRIBUTE = "gateway.authenticated";
    public static final String AUTHENTICATED_JWT_ATTRIBUTE = "gateway.authenticatedJwt";

    private final ReactiveJwtDecoder jwtDecoder;
    private final GatewayErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(ReactiveJwtDecoder jwtDecoder, GatewayErrorResponseWriter errorResponseWriter) {
        this.jwtDecoder = jwtDecoder;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Allow CORS preflight to pass through unauthenticated.
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        Object authenticatedMarker = exchange.getAttribute(AUTHENTICATED_MARKER_ATTRIBUTE);
        Object authenticatedJwt = exchange.getAttribute(AUTHENTICATED_JWT_ATTRIBUTE);
        if (Boolean.TRUE.equals(authenticatedMarker) && authenticatedJwt instanceof Jwt cachedJwt) {
            return authenticateAndContinue(exchange, chain, cachedJwt);
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
        SecurityContextImpl securityContext = new SecurityContextImpl(authentication);

        exchange.getAttributes().put(AUTHENTICATED_MARKER_ATTRIBUTE, Boolean.TRUE);
        exchange.getAttributes().put(AUTHENTICATED_JWT_ATTRIBUTE, jwt);
        exchange.getAttributes().put(ExchangeAttributeSecurityContextRepository.ATTRIBUTE_NAME, securityContext);

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
        return PublicEndpointPaths.isPublicPath(path);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return errorResponseWriter.write(exchange, 401, "Unauthorized", "Unauthorized");
    }
}
