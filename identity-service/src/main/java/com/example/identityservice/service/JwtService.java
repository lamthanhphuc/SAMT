package com.example.identityservice.service;

import com.example.identityservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.example.identityservice.security.JwtKeyMaterial;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * JWT Service for token generation and validation.
 * @see docs/Authentication-Authorization-Design.md - JWT Specification
 * 
 * Algorithm: RS256
 * Signing Key: RSA private key (Identity Service only)
 * Access Token TTL: 15 minutes
 */
@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final long accessTokenExpiration;

    public JwtService(
            JwtKeyMaterial keyMaterial,
            @Value("${jwt.access-token-expiration:900000}") long accessTokenExpiration) {
        this.privateKey = keyMaterial.privateKey();
        this.publicKey = keyMaterial.publicKey();
        this.keyId = keyMaterial.keyId();
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
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
            .header()
            .type("JWT")
            .keyId(keyId)
            .and()
            .id(jti)                                                // jti
                .issuer("identity-service")                              // iss: trusted issuer
                .audience().add("api-gateway").and()                     // aud: intended audience
                .subject(String.valueOf(user.getId()))                    // sub: User ID
                .claim("email", user.getEmail())                          // email: User email
                .claim("roles", List.of(user.getRole().name()))           // roles: List of roles
                .claim("token_type", "ACCESS")                            // token_type: ACCESS
                .issuedAt(now)                                            // iat: Issued at
                .expiration(expiration)                                   // exp: Expiration
            .signWith(privateKey, Jwts.SIG.RS256)                      // Algorithm: RS256
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
            .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
