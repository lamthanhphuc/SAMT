package com.example.gateway.validation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class JwtSecretValidator implements InitializingBean {

    @Value("${jwt.jwks-uri}")
    private String jwksUri;

    @Override
    public void afterPropertiesSet() {
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("JWT_JWKS_URI environment variable must be set (non-blank).");
        }
    }
}
