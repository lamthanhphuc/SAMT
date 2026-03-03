package com.example.gateway.validation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Profile("prod")
public class GatewaySecretValidator implements InitializingBean {
    @Value("${gateway.internal.secret}")
    private String gatewaySecret;

    @Override
    public void afterPropertiesSet() {
        if (gatewaySecret == null || gatewaySecret.isBlank()) {
            throw new IllegalStateException("INTERNAL_SIGNING_SECRET environment variable must be set (non-blank).");
        }

        if (gatewaySecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("INTERNAL_SIGNING_SECRET must be at least 256 bits in production.");
        }
    }
}
