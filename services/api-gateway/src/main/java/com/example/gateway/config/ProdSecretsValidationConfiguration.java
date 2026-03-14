package com.example.gateway.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Profile("prod")
@EnableConfigurationProperties(ProdSecretsProperties.class)
public class ProdSecretsValidationConfiguration {

    @Bean
    ApplicationRunner prodSecretsValidator(ProdSecretsProperties secrets) {
        return args -> {
            Path pemPath = Path.of(secrets.getGatewayInternalJwtPrivateKeyPemPath());
            if (!Files.exists(pemPath)) {
                throw new IllegalStateException("GATEWAY_INTERNAL_JWT_PRIVATE_KEY_PEM_PATH does not exist: " + pemPath);
            }
            if (Files.isDirectory(pemPath)) {
                throw new IllegalStateException("GATEWAY_INTERNAL_JWT_PRIVATE_KEY_PEM_PATH must be a file: " + pemPath);
            }
            if (secrets.getGatewayInternalJwtKid().isBlank()) {
                throw new IllegalStateException("GATEWAY_INTERNAL_JWT_KID must be set");
            }
        };
    }
}
