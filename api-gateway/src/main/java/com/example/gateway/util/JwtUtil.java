package com.example.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        validateJwtSecretStrength(secret);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Validates JWT secret meets minimum security requirements
     */
    private void validateJwtSecretStrength(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT secret cannot be null or empty. Please set JWT_SECRET environment variable.");
        }
        
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        final int MINIMUM_KEY_LENGTH = 32; // 256 bits
        final int RECOMMENDED_KEY_LENGTH = 64; // 512 bits
        
        if (secretBytes.length < MINIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException(
                String.format("JWT secret must be at least %d bytes (256 bits) for security. " +
                             "Current secret is %d bytes. Please use a stronger JWT_SECRET.", 
                             MINIMUM_KEY_LENGTH, secretBytes.length)
            );
        }
        
        if (secretBytes.length < RECOMMENDED_KEY_LENGTH) {
            log.warn("JWT secret length is {} bytes. Consider using at least {} bytes (512 bits) for enhanced security.", 
                     secretBytes.length, RECOMMENDED_KEY_LENGTH);
        }
    }

    /**
     * Validate + parse JWT
     * Return null nếu invalid
     */
    public Claims validateAndParseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // Sanitize logging to prevent token exposure
            logJwtValidationFailure(e);
            return null; // không throw
        }
    }
    
    /**
     * Safely log JWT validation failures without exposing sensitive token data
     */
    private void logJwtValidationFailure(Exception e) {
        String safeName = e.getClass().getSimpleName();
        
        // Map exception types to safe error messages
        String safeReason;
        if (e instanceof io.jsonwebtoken.ExpiredJwtException) {
            safeReason = "Token expired";
        } else if (e instanceof io.jsonwebtoken.MalformedJwtException) {
            safeReason = "Token malformed";
        } else if (e instanceof io.jsonwebtoken.SignatureException) {
            safeReason = "Invalid signature";
        } else if (e instanceof io.jsonwebtoken.UnsupportedJwtException) {
            safeReason = "Unsupported token format";
        } else if (e instanceof io.jsonwebtoken.security.SecurityException) {
            safeReason = "Security validation failed";
        } else if (e instanceof IllegalArgumentException) {
            safeReason = "Invalid token argument";
        } else {
            safeReason = "Token validation error";
        }
        
        log.debug("JWT validation failed: {} ({})", safeReason, safeName);
    }
}