package com.example.analysisservice.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "ai")
public class LocalAiProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String model;

    @Min(100)
    private int timeoutMs;
}
