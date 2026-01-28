package com.example.identityservice.service;

import com.example.identityservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT Service for token generation and validation.
 * @see docs/Authentication-Authorization-Design.md - JWT Specification
 * 
 * Algorithm: HS256
 * Signing Key: JWT_SECRET (environment variable)
 * Access Token TTL: 15 minutes
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.access-token-expiration:900000}") long accessTokenExpiration) {
        // HS256 requires at least 256 bits (32 bytes) key
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration; // Default: 900000ms = 15 minutes
    }

    /**
     * Generate Access Token (JWT) for authenticated user.
     * 
     * Claims (from Auth Design doc):
     * - sub: User ID
     * - email: User email
     * - roles: List of roles (ROLE_*)
     * - iat: Issued at
     * - exp: Expiration
     * - token_type: ACCESS
     *
     * @param user Authenticated user
     * @return JWT access token string
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))                    // sub: User ID
                .claim("email", user.getEmail())                          // email: User email
                .claim("roles", List.of(user.getRole().name()))           // roles: List of roles
                .claim("token_type", "ACCESS")                            // token_type: ACCESS
                .issuedAt(now)                                            // iat: Issued at
                .expiration(expiration)                                   // exp: Expiration
                .signWith(secretKey)                                      // Algorithm: HS256
                .compact();
    }

    /**
     * Extract user ID from token.
     *
     * @param token JWT token
     * @return User ID
     */
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extract email from token.
     *
     * @param token JWT token
     * @return User email
     */
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Validate token signature and expiration.
     *
     * @param token JWT token
     * @return true if valid
     */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if token is expired.
     *
     * @param token JWT token
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Extract all claims from token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
