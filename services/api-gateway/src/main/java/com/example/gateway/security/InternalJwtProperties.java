package com.example.gateway.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.internal-jwt")
public class InternalJwtProperties {

    /**
     * Token lifetime in seconds. Keep very short (15-30s).
     */
    @Min(5)
    @Max(60)
    private int ttlSeconds = 20;

    /**
     * Maximum clock skew allowed when validating tokens (downstream should use the same).
     */
    @Min(0)
    @Max(30)
    private int clockSkewSeconds = 30;

    /**
     * Gateway logical issuer name embedded as claim "service".
     */
    @NotBlank
    private String serviceName = "api-gateway";

    /**
     * JWT issuer (iss). Should be stable across key rotations.
     */
    @NotBlank
    private String issuer = "samt-gateway";

    /**
     * PEM file path to RSA private key used to sign internal JWT.
     */
    @NotBlank
    private String privateKeyPemPath;

    /**
     * Key id to publish via JWKS and set in JWS header (kid).
     */
    @NotBlank
    private String keyId;

    /**
     * Optional path to a JWKS JSON file containing additional PUBLIC keys to publish.
     *
     * Use this for rotation overlap: publish both old + new public keys while only signing with the active key.
     */
    private String additionalPublicJwksJsonPath;

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getPrivateKeyPemPath() {
        return privateKeyPemPath;
    }

    public void setPrivateKeyPemPath(String privateKeyPemPath) {
        this.privateKeyPemPath = privateKeyPemPath;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getAdditionalPublicJwksJsonPath() {
        return additionalPublicJwksJsonPath;
    }

    public void setAdditionalPublicJwksJsonPath(String additionalPublicJwksJsonPath) {
        this.additionalPublicJwksJsonPath = additionalPublicJwksJsonPath;
    }
}
