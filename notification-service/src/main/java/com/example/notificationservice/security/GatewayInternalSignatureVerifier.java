package com.example.notificationservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class GatewayInternalSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final String expectedKeyId;
    private final long maxSkewSeconds;

    public GatewayInternalSignatureVerifier(
            @Value("${internal.signing.secret}") String secret,
            @Value("${internal.signing.key-id:gateway-1}") String expectedKeyId,
            @Value("${internal.signing.max-skew-seconds:300}") long maxSkewSeconds
    ) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("INTERNAL_SIGNING_SECRET must be configured for gateway trust.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expectedKeyId = expectedKeyId;
        this.maxSkewSeconds = maxSkewSeconds;
    }

    public boolean verify(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        String originalPath = request.getHeader("X-Internal-Original-Path");
        String timestamp = request.getHeader("X-Internal-Timestamp");
        String signature = request.getHeader("X-Internal-Signature");
        String keyId = request.getHeader("X-Internal-Key-Id");

        if (!StringUtils.hasText(userId)
                || !StringUtils.hasText(role)
                || !StringUtils.hasText(originalPath)
                || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(signature)
                || !StringUtils.hasText(keyId)) {
            return false;
        }

        if (!expectedKeyId.equals(keyId)) {
            return false;
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }

        long now = Instant.now().getEpochSecond();
        long skew = Math.abs(now - ts);
        if (skew > maxSkewSeconds) {
            return false;
        }

        String method = request.getMethod();
        String bodyHash = sha256Hex(new byte[0]);
        String payload = ts + "|" + (method == null ? "" : method.toUpperCase()) + "|" + originalPath + "|" + bodyHash;
        String expectedSig = hmacSha256Hex(secret, payload.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(byte[] secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] out = mac.doFinal(payload);
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
