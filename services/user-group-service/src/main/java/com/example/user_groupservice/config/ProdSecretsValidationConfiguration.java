package com.example.user_groupservice.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
@EnableConfigurationProperties(ProdSecretsProperties.class)
public class ProdSecretsValidationConfiguration {

    @Bean
    ApplicationRunner prodSecretsValidator(ProdSecretsProperties secrets) {
        return args -> {
            // No-op: keep class for any future prod secret checks.
        };
    }
}
