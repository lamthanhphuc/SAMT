package com.example.gateway.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.charset.StandardCharsets;

@Configuration
@Profile("prod")
@EnableConfigurationProperties(ProdSecretsProperties.class)
public class ProdSecretsValidationConfiguration {

    @Bean
    ApplicationRunner prodSecretsValidator(ProdSecretsProperties secrets) {
        return args -> {
            if (secrets.getInternalSigningSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("INTERNAL_SIGNING_SECRET must be at least 256 bits in production.");
            }
        };
    }
}
