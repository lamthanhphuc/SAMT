package com.example.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "samt.secrets")
public class ProdSecretsProperties {

    @NotBlank(message = "JWT_JWKS_URI environment variable must be set (non-blank)")
    private String jwtJwksUri;

    @NotBlank(message = "INTERNAL_SIGNING_SECRET environment variable must be set (non-blank)")
    private String internalSigningSecret;

    public String getJwtJwksUri() {
        return jwtJwksUri;
    }

    public void setJwtJwksUri(String jwtJwksUri) {
        this.jwtJwksUri = jwtJwksUri;
    }

    public String getInternalSigningSecret() {
        return internalSigningSecret;
    }

    public void setInternalSigningSecret(String internalSigningSecret) {
        this.internalSigningSecret = internalSigningSecret;
    }
}
