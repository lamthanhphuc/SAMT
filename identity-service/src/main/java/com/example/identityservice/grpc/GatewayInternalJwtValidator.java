package com.example.identityservice.grpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class GatewayInternalJwtValidator {

    private static final Duration JWK_CACHE_TTL = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String jwksUri;
    private final String expectedIssuer;
    private final String expectedService;

    private volatile Instant cacheExpiresAt = Instant.EPOCH;
    private volatile Map<String, RSAPublicKey> cachedKeys = Map.of();

    public GatewayInternalJwtValidator(
            ObjectMapper objectMapper,
            @Value("${gateway.internal-jwt.jwks-uri:http://api-gateway:8080/.well-known/internal-jwks.json}") String jwksUri,
            @Value("${gateway.internal-jwt.expected-issuer:samt-gateway}") String expectedIssuer,
            @Value("${gateway.internal-jwt.expected-service:api-gateway}") String expectedService) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.jwksUri = jwksUri;
        this.expectedIssuer = expectedIssuer;
        this.expectedService = expectedService;
    }

    public Long validateAndExtractUserId(String token) {
        try {
            String keyId = extractKeyId(token);
            RSAPublicKey publicKey = resolvePublicKey(keyId);
            if (publicKey == null) {
                return null;
            }

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedIssuer.equals(claims.getIssuer())) {
                return null;
            }

            String service = claims.get("service", String.class);
            if (!expectedService.equals(service)) {
                return null;
            }

            return Long.parseLong(claims.getSubject());
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractKeyId(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed JWT");
        }

        byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
        JsonNode headerNode = objectMapper.readTree(headerBytes);
        JsonNode kidNode = headerNode.get("kid");
        return kidNode == null || kidNode.isNull() ? null : kidNode.asText();
    }

    private RSAPublicKey resolvePublicKey(String keyId) throws Exception {
        Map<String, RSAPublicKey> keys = cachedKeys;
        Instant now = Instant.now();
        if (now.isAfter(cacheExpiresAt) || !keys.containsKey(keyId)) {
            synchronized (this) {
                if (now.isAfter(cacheExpiresAt) || !cachedKeys.containsKey(keyId)) {
                    cachedKeys = fetchKeys();
                    cacheExpiresAt = now.plus(JWK_CACHE_TTL);
                }
                keys = cachedKeys;
            }
        }

        if (keyId != null) {
            return keys.get(keyId);
        }

        Iterator<RSAPublicKey> iterator = keys.values().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    private Map<String, RSAPublicKey> fetchKeys() throws Exception {
        String jwksJson = restClient.get()
                .uri(jwksUri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(jwksJson);
        JsonNode keysNode = root.path("keys");
        Map<String, RSAPublicKey> keys = new HashMap<>();
        for (JsonNode keyNode : keysNode) {
            String kid = keyNode.path("kid").asText(null);
            String kty = keyNode.path("kty").asText(null);
            String modulus = keyNode.path("n").asText(null);
            String exponent = keyNode.path("e").asText(null);
            if (!"RSA".equals(kty) || kid == null || modulus == null || exponent == null) {
                continue;
            }
            keys.put(kid, buildRsaPublicKey(modulus, exponent));
        }
        return keys;
    }

    private RSAPublicKey buildRsaPublicKey(String modulus, String exponent) throws GeneralSecurityException {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                new BigInteger(1, modulusBytes),
                new BigInteger(1, exponentBytes)
        );
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }
}