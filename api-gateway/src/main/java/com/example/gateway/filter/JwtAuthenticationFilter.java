package com.example.gateway.filter;

import com.example.gateway.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Separate audit logger for security events
    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT.AUTH");

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/identity/login",
            "/api/identity/register",
            "/api/identity/refresh-token"
    );

    @Override
    public int getOrder() {
        return OrderedFilters.JWT;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        // Allow public endpoints without authentication
        if (PUBLIC_ENDPOINTS.contains(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange.getRequest());

        // FAIL-CLOSED: Return 401 if no token provided
        if (token == null) {
            auditAuthenticationFailure(exchange, null, "MISSING_TOKEN", "No JWT token provided");
            return createUnauthorizedResponse(exchange, "Missing JWT token");
        }

        Claims claims = jwtUtil.validateAndParseClaims(token);

        // FAIL-CLOSED: Return 401 if token is invalid
        if (claims == null) {
            auditAuthenticationFailure(exchange, null, "INVALID_TOKEN", "JWT token validation failed");
            return createUnauthorizedResponse(exchange, "Invalid JWT token");
        }

        Long userId = claims.get("userId", Long.class);
        String role = claims.get("role", String.class);

        // Audit successful authentication
        auditAuthenticationSuccess(exchange, userId, role);

        List<SimpleGrantedAuthority> authorities =
                role != null
                        ? List.of(new SimpleGrantedAuthority(role))
                        : Collections.emptyList();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

        return chain.filter(exchange)
                .contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(authentication)
                );
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private Mono<Void> createUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now().toString());
        errorBody.put("status", 401);
        errorBody.put("error", "Unauthorized");
        errorBody.put("message", message);
        errorBody.put("path", exchange.getRequest().getURI().getPath());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            // Fallback to simple response if JSON serialization fails
            byte[] fallbackBytes = "{\"error\":\"Unauthorized\"}".getBytes();
            DataBuffer buffer = response.bufferFactory().wrap(fallbackBytes);
            return response.writeWith(Mono.just(buffer));
        }
    }
    
    /**
     * Audit successful authentication event
     */
    private void auditAuthenticationSuccess(ServerWebExchange exchange, Long userId, String role) {
        Map<String, Object> auditEvent = new HashMap<>();
        auditEvent.put("timestamp", LocalDateTime.now().toString());
        auditEvent.put("event", "AUTHENTICATION_SUCCESS");
        auditEvent.put("userId", userId);
        auditEvent.put("role", role);
        auditEvent.put("clientIp", getClientIp(exchange.getRequest()));
        auditEvent.put("path", exchange.getRequest().getURI().getPath());
        auditEvent.put("method", exchange.getRequest().getMethod().toString());
        auditEvent.put("userAgent", exchange.getRequest().getHeaders().getFirst("User-Agent"));
        
        try {
            String auditJson = objectMapper.writeValueAsString(auditEvent);
            AUDIT_LOGGER.info(auditJson);
        } catch (Exception e) {
            AUDIT_LOGGER.info("AUTHENTICATION_SUCCESS userId={} role={} ip={} path={}", 
                            userId, role, getClientIp(exchange.getRequest()), 
                            exchange.getRequest().getURI().getPath());
        }
    }
    
    /**
     * Audit failed authentication event
     */
    private void auditAuthenticationFailure(ServerWebExchange exchange, Long userId, String reason, String details) {
        Map<String, Object> auditEvent = new HashMap<>();
        auditEvent.put("timestamp", LocalDateTime.now().toString());
        auditEvent.put("event", "AUTHENTICATION_FAILURE");
        auditEvent.put("userId", userId);
        auditEvent.put("reason", reason);
        auditEvent.put("details", details);
        auditEvent.put("clientIp", getClientIp(exchange.getRequest()));
        auditEvent.put("path", exchange.getRequest().getURI().getPath());
        auditEvent.put("method", exchange.getRequest().getMethod().toString());
        auditEvent.put("userAgent", exchange.getRequest().getHeaders().getFirst("User-Agent"));
        
        try {
            String auditJson = objectMapper.writeValueAsString(auditEvent);
            AUDIT_LOGGER.warn(auditJson);
        } catch (Exception e) {
            AUDIT_LOGGER.warn("AUTHENTICATION_FAILURE userId={} reason={} ip={} path={}", 
                            userId, reason, getClientIp(exchange.getRequest()), 
                            exchange.getRequest().getURI().getPath());
        }
    }
    
    /**
     * Get client IP address from request headers or remote address
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null 
            ? request.getRemoteAddress().getAddress().getHostAddress() 
            : "unknown";
    }
}