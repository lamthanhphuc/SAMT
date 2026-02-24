package com.example.gateway.validation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewaySecretValidator implements InitializingBean {
    @Value("${gateway.internal.secret}")
    private String gatewaySecret;

    @Override
    public void afterPropertiesSet() {
        if (gatewaySecret == null || gatewaySecret.isEmpty()) {
            throw new IllegalStateException("Gateway internal secret is not configured");
        }
        if (gatewaySecret.length() < 32) {
            throw new IllegalStateException("Gateway internal secret must be at least 256 bits (32 characters)");
        }
    }
}
