package com.example.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Utility cho API Gateway
 * 
 * Chỉ validate và parse JWT (không generate)
 * Secret key phải giống với Identity Service
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // CRITICAL: Secret key MUST match Identity Service
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validate JWT signature và expiration, sau đó extract claims
     */
    public Claims validateAndParseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid or expired JWT token", e);
        }
    }

    /**
     * Extract userId từ JWT token
     */
    public Long extractUserId(String token) {
        Claims claims = validateAndParseClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * Extract role từ JWT token
     */
    public String extractRole(String token) {
        Claims claims = validateAndParseClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * Extract email từ JWT token
     */
    public String extractEmail(String token) {
        Claims claims = validateAndParseClaims(token);
        return claims.getSubject();
    }
}
