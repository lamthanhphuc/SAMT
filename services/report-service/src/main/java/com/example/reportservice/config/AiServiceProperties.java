package com.example.reportservice.config;



import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "ai.service")
@Validated
@Data
public class AiServiceProperties {

    @NotBlank
    private String url;

    @Min(100)
    private int timeout;
}
