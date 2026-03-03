package com.example.identityservice.config;

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
            // Binding + @NotBlank on ProdSecretsProperties already provides fail-fast validation
            // for required production environment variables (e.g., datasource password).
        };
    }
}
