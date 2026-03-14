package com.example.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "samt.secrets")
public class ProdSecretsProperties {

    @NotBlank(message = "JWT_JWKS_URI environment variable must be set (non-blank)")
    private String jwtJwksUri;

    @NotBlank(message = "GATEWAY_INTERNAL_JWT_PRIVATE_KEY_PEM_PATH must be set (non-blank)")
    private String gatewayInternalJwtPrivateKeyPemPath;

    @NotBlank(message = "GATEWAY_INTERNAL_JWT_KID must be set (non-blank)")
    private String gatewayInternalJwtKid;

    public String getJwtJwksUri() {
        return jwtJwksUri;
    }

    public void setJwtJwksUri(String jwtJwksUri) {
        this.jwtJwksUri = jwtJwksUri;
    }

    public String getGatewayInternalJwtPrivateKeyPemPath() {
        return gatewayInternalJwtPrivateKeyPemPath;
    }

    public void setGatewayInternalJwtPrivateKeyPemPath(String gatewayInternalJwtPrivateKeyPemPath) {
        this.gatewayInternalJwtPrivateKeyPemPath = gatewayInternalJwtPrivateKeyPemPath;
    }

    public String getGatewayInternalJwtKid() {
        return gatewayInternalJwtKid;
    }

    public void setGatewayInternalJwtKid(String gatewayInternalJwtKid) {
        this.gatewayInternalJwtKid = gatewayInternalJwtKid;
    }
}
