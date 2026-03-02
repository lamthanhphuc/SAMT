package com.example.gateway.filter;

import com.example.gateway.util.SignedHeaderUtil;
import io.jsonwebtoken.Claims;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(-50)
public class SignedHeaderFilter implements WebFilter, Ordered {

    private final SignedHeaderUtil signedHeaderUtil;

    public SignedHeaderFilter(SignedHeaderUtil signedHeaderUtil) {
        this.signedHeaderUtil = signedHeaderUtil;
    }

    @Override
    public int getOrder() {
        return -50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Object authenticatedMarker = exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_MARKER_ATTRIBUTE);
        Object claimsAttribute = exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE);

        if (!(authenticatedMarker instanceof Boolean isAuthenticated)
                || !isAuthenticated
                || !(claimsAttribute instanceof Claims claims)) {
            return chain.filter(exchange);
        }

        String userId = readUserId(claims);
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        if (!StringUtils.hasText(email)) {
            email = claims.getSubject();
        }

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(email) || !StringUtils.hasText(role)) {
            return chain.filter(exchange);
        }

        // Generate deterministic signed headers
        ServerHttpRequest request = exchange.getRequest();
        long timestampSeconds = signedHeaderUtil.currentTimestampSeconds();
        String httpMethod = request.getMethod().toString();
        String path = request.getURI().getPath();
        String bodyHash = signedHeaderUtil.generateEmptyBodyHash(); // For simplicity, using empty body hash
        
        String signature = signedHeaderUtil.generateSignature(timestampSeconds, httpMethod, path, bodyHash);
        String keyId = signedHeaderUtil.getKeyId();

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        // Inject ALL required internal headers (sanitization handled by HeaderTrustBoundaryFilter)
        requestBuilder
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .header("X-Internal-Timestamp", String.valueOf(timestampSeconds))
                .header("X-Internal-Signature", signature)
                .header("X-Internal-Key-Id", keyId);

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
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
}
