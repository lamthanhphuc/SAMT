package com.example.gateway.util;

import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SignatureUtil {
    private final String gatewaySecret;

    public SignatureUtil(@Value("${gateway.internal.secret}") String gatewaySecret) {
        if (gatewaySecret == null || gatewaySecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Gateway internal secret cannot be null or empty. Please set GATEWAY_INTERNAL_SECRET environment variable.");
        }
        this.gatewaySecret = gatewaySecret;
    }

    public String generateSignature(Long userId, String email, String role, long timestamp) {
        String payload = userId + "|" + email + "|" + role + "|" + timestamp;
        return HmacUtils.hmacSha256Hex(gatewaySecret.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8));
    }

    public boolean verifySignature(Long userId, String email, String role, long timestamp, String receivedSignature) {
        String expectedSignature = generateSignature(userId, email, role, timestamp);
        return expectedSignature.equals(receivedSignature);
    }
}
