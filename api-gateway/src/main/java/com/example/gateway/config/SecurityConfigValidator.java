package com.example.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Validates critical security configuration at application startup.
 * Ensures all required secrets are present and meet minimum security requirements.
 * Application will fail to start if validation fails - this is intentional for security.
 */
@Component
@Slf4j
public class SecurityConfigValidator {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${gateway.internal.secret:}")
    private String gatewayInternalSecret;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String corsAllowedOrigins;

    @EventListener(ApplicationReadyEvent.class)
    public void validateSecurityConfiguration() {
        log.info("Performing security configuration validation...");

        // Validate JWT Secret
        validateJwtSecret();
        
        // Validate Gateway Internal Secret  
        validateGatewayInternalSecret();
        
        // Validate Redis Password
        validateRedisPassword();
        
        // Validate CORS Configuration
        validateCorsConfiguration();

        log.info("✅ All security configuration validation passed!");
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("❌ SECURITY: JWT_SECRET environment variable is required but not set. " +
                                          "Generate with: openssl rand -base64 64");
        }

        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        final int MINIMUM_KEY_LENGTH = 32; // 256 bits
        final int RECOMMENDED_KEY_LENGTH = 64; // 512 bits

        if (secretBytes.length < MINIMUM_KEY_LENGTH) {
            throw new IllegalStateException(
                String.format("❌ SECURITY: JWT_SECRET must be at least %d bytes (256 bits). " +
                             "Current: %d bytes. Generate with: openssl rand -base64 64", 
                             MINIMUM_KEY_LENGTH, secretBytes.length)
            );
        }

        if (secretBytes.length < RECOMMENDED_KEY_LENGTH) {
            log.warn("⚠️  SECURITY: JWT_SECRET is {} bytes. Recommended: {} bytes (512 bits) for enhanced security.", 
                     secretBytes.length, RECOMMENDED_KEY_LENGTH);
        }

        log.info("✅ JWT_SECRET validation passed ({} bytes)", secretBytes.length);
    }

    private void validateGatewayInternalSecret() {
        if (gatewayInternalSecret == null || gatewayInternalSecret.trim().isEmpty()) {
            throw new IllegalStateException("❌ SECURITY: GATEWAY_INTERNAL_SECRET environment variable is required but not set. " +
                                          "Generate with: openssl rand -base64 32");
        }

        byte[] secretBytes = gatewayInternalSecret.getBytes(StandardCharsets.UTF_8);
        final int MINIMUM_LENGTH = 16; // 128 bits minimum

        if (secretBytes.length < MINIMUM_LENGTH) {
            throw new IllegalStateException(
                String.format("❌ SECURITY: GATEWAY_INTERNAL_SECRET must be at least %d bytes. " +
                             "Current: %d bytes. Generate with: openssl rand -base64 32", 
                             MINIMUM_LENGTH, secretBytes.length)
            );
        }

        log.info("✅ GATEWAY_INTERNAL_SECRET validation passed ({} bytes)", secretBytes.length);
    }

    private void validateRedisPassword() {
        if (redisPassword == null || redisPassword.trim().isEmpty()) {
            throw new IllegalStateException("❌ SECURITY: REDIS_PASSWORD environment variable is required but not set. " +
                                          "Redis authentication is mandatory. Generate with: openssl rand -base64 32");
        }

        if (redisPassword.length() < 8) {
            throw new IllegalStateException("❌ SECURITY: REDIS_PASSWORD must be at least 8 characters. " +
                                          "Current: " + redisPassword.length() + " characters. " +
                                          "Generate with: openssl rand -base64 32");
        }

        log.info("✅ REDIS_PASSWORD validation passed ({} characters)", redisPassword.length());
    }

    private void validateCorsConfiguration() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.trim().isEmpty()) {
            log.warn("⚠️  SECURITY: CORS_ALLOWED_ORIGINS not set. Using secure localhost fallback. " +
                     "Set CORS_ALLOWED_ORIGINS for production.");
            return;
        }

        // Check for dangerous wildcard
        if (corsAllowedOrigins.contains("*")) {
            throw new IllegalStateException("❌ SECURITY: CORS_ALLOWED_ORIGINS contains wildcard '*' which is not allowed. " +
                                          "Specify exact origins: https://app.domain.com,https://admin.domain.com");
        }

        // Validate each origin format
        String[] origins = corsAllowedOrigins.split(",");
        for (String origin : origins) {
            origin = origin.trim();
            if (!origin.startsWith("http://") && !origin.startsWith("https://")) {
                throw new IllegalStateException("❌ SECURITY: Invalid CORS origin format: " + origin + 
                                              ". Must start with http:// or https://");
            }
            
            // Warn about http in production
            if (origin.startsWith("http://") && !origin.contains("localhost")) {
                log.warn("⚠️  SECURITY: CORS origin uses HTTP (not HTTPS): {}. Consider HTTPS for production.", origin);
            }
        }

        log.info("✅ CORS_ALLOWED_ORIGINS validation passed ({} origins)", origins.length);
    }
}