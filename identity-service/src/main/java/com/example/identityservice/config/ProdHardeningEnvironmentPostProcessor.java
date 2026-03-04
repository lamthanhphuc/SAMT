package com.example.identityservice.config;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

public class ProdHardeningEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "identityServiceProdHardening";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        Map<String, Object> enforced = Map.of(
                "springdoc.api-docs.enabled", "false",
                "springdoc.swagger-ui.enabled", "false",
                "management.endpoints.web.exposure.include", "health,info",
                "management.endpoint.health.show-details", "never"
        );

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, enforced));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
