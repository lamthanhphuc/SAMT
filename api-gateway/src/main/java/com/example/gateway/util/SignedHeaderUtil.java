package com.example.gateway.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class SignedHeaderUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";

    private final byte[] hmacSecret;
    private final String keyId;
    private final String emptyBodyHash;

    public SignedHeaderUtil(
            @Value("${internal.signing.secret:${gateway.internal.secret}}") String internalSigningSecret,
            @Value("${internal.signing.key-id:gateway-1}") String keyId
    ) {
        if (internalSigningSecret == null || internalSigningSecret.isBlank()) {
            throw new IllegalStateException("Internal signing secret is not configured.");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("Internal signing key-id is not configured.");
        }

        this.hmacSecret = internalSigningSecret.getBytes(StandardCharsets.UTF_8);
        this.keyId = keyId;
        this.emptyBodyHash = sha256Hex(new byte[0]);
    }

    public long currentTimestampSeconds() {
        return Instant.now().getEpochSecond();
    }

    /**
     * Returns the SHA-256 hex digest of an empty body.
     * This is intentionally deterministic and byte-based.
     */
    public String generateEmptyBodyHash() {
        return emptyBodyHash;
    }

    public String getKeyId() {
        return keyId;
    }

    /**
     * Generates an HMAC-SHA256 signature for internal gateway headers.
     *
     * Canonical payload format (UTF-8 encoded):
     *   timestampSeconds|METHOD|/path|bodyHash
     */
    public String generateSignature(long timestampSeconds, String httpMethod, String path, String bodyHash) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        String canonicalPath = path == null ? "" : path;
        String canonicalBodyHash = bodyHash == null ? "" : bodyHash;

        String payload = timestampSeconds + "|" + method + "|" + canonicalPath + "|" + canonicalBodyHash;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] mac = hmacSha256(hmacSecret, payloadBytes);
        return HexFormat.of().formatHex(mac);
    }

    private static byte[] hmacSha256(byte[] secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (Exception e) {
            // This should never happen with a supported JDK; treat as fatal.
            throw new IllegalStateException("Unable to compute internal signature.");
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available.");
        }
    }
}
