package com.samt.projectconfig.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.internal-jwt")
public class InternalJwtValidationProperties {

    @NotBlank
    private String issuer = "samt-gateway";

    @NotBlank
    private String expectedService = "api-gateway";

    @Min(0)
    @Max(30)
    private int clockSkewSeconds = 30;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getExpectedService() {
        return expectedService;
    }

    public void setExpectedService(String expectedService) {
        this.expectedService = expectedService;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }
}
