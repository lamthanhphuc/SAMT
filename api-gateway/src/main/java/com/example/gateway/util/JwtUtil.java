package com.example.gateway.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final ObjectMapper objectMapper;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.objectMapper = new ObjectMapper();
    }

    public Claims validateAndParseClaims(String token) {
        try {
            validateHs256Header(token);
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Validate required claims
            if (claims.get("userId") == null
                    || !StringUtils.hasText(claims.getSubject())
                    || !StringUtils.hasText(claims.get("role", String.class))) {
                throw new IllegalArgumentException("Unauthorized");
            }

            // CRITICAL: Validate token_type == ACCESS
            String tokenType = claims.get("token_type", String.class);
            if (!"ACCESS".equals(tokenType)) {
                throw new IllegalArgumentException("Invalid token type");
            }

            return claims;
        } catch (ExpiredJwtException e) {
            throw new IllegalArgumentException("Unauthorized");
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException as is (preserves specific messages)
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unauthorized");
        }
    }

    private void validateHs256Header(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid or expired JWT token");
        }

        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode header = objectMapper.readTree(headerJson);
            String alg = header.path("alg").asText(null);
            if (!"HS256".equals(alg)) {
                throw new IllegalArgumentException("Invalid or expired JWT token");
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid or expired JWT token");
        }
    }
}
