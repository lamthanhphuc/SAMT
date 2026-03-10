package com.example.reportservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
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
}
