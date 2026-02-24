package com.example.gateway.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * JWT Secret Validator
 * 
 * Validates JWT secret configuration at application startup to ensure:
 * 1. Secret is not null or empty
 * 2. Secret meets minimum length requirement (256 bits = 32 bytes = 43 base64 chars)
 * 3. Secret is not using default insecure value
 * 
 * CRITICAL: JWT_SECRET must be IDENTICAL across all services:
 * - api-gateway (this service)
 * - identity-service (issues tokens)
 * - user-group-service (validates tokens)
 * - project-config-service (validates tokens)
 * 
 * If secrets differ, tokens issued by Identity Service will be rejected by other services.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518#section-3.2">RFC 7518 - HS256 requires 256-bit key</a>
 */
@Component
public class JwtSecretValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);

    /**
     * Minimum secret length for HS256 (256 bits)
     * Base64 encoding: 256 bits / 6 bits per char ≈ 43 characters
     */
    private static final int MIN_SECRET_LENGTH = 43;

    /**
     * Known insecure default secrets (should never be used in production)
     */
    private static final String[] INSECURE_DEFAULTS = {
        "secret",
        "your-256-bit-secret",
        "your-256-bit-secret-key-change-this-in-production",
        "change-this-in-production",
        "default-secret-key"
    };

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("🔐 Validating JWT secret configuration...");

        try {
            validateJwtSecret();
            log.info("✅ JWT secret validation: PASSED");
            log.info("📏 JWT secret length: {} characters", jwtSecret.length());
        } catch (IllegalStateException e) {
            log.error("❌ JWT SECRET VALIDATION FAILED: {}", e.getMessage());
            log.error("🔴 APPLICATION STARTUP ABORTED - Fix JWT secret configuration!");
            throw e;  // Halt application startup
        }
    }

    private void validateJwtSecret() {
        // Check 1: Secret must not be null or empty
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                "JWT_SECRET is not configured! " +
                "Set environment variable JWT_SECRET or application property jwt.secret"
            );
        }

        // Check 2: Secret must meet minimum length (256 bits for HS256)
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                String.format(
                    "JWT_SECRET is too short (%d characters). " +
                    "HS256 requires at least 256 bits (≈43 base64 characters). " +
                    "Current length: %d. Generate strong secret: openssl rand -base64 32",
                    jwtSecret.length(), jwtSecret.length()
                )
            );
        }

        // Check 3: Secret must not be a known insecure default
        for (String insecureDefault : INSECURE_DEFAULTS) {
            if (jwtSecret.equals(insecureDefault)) {
                throw new IllegalStateException(
                    String.format(
                        "JWT_SECRET is using insecure default value: '%s'. " +
                        "Generate a strong secret: openssl rand -base64 32",
                        insecureDefault
                    )
                );
            }
        }

        // Check 4: Warn if secret looks suspicious (all lowercase, no special chars, etc.)
        if (jwtSecret.matches("^[a-z0-9]+$")) {
            log.warn("⚠️  JWT_SECRET appears weak (only lowercase letters and numbers). " +
                     "Consider using a cryptographically strong secret generated with: openssl rand -base64 32");
        }

        // All checks passed
        log.debug("JWT secret passes all validation checks");
    }
}
