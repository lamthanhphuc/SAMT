package com.example.common.security.kafka;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class ProdKafkaSecurityEnforcerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Log log = LogFactory.getLog(ProdKafkaSecurityEnforcerEnvironmentPostProcessor.class);

    private static final String TRUSTED_PACKAGES_KEY_FRAGMENT = "spring.json.trusted.packages";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        Set<String> offendingKeys = new LinkedHashSet<>();

        // Fast path for the canonical Boot property
        String canonical = environment.getProperty("spring.kafka.consumer.properties.spring.json.trusted.packages");
        if (containsWildcard(canonical)) {
            offendingKeys.add("spring.kafka.consumer.properties.spring.json.trusted.packages");
        }

        // Defensive scan across all property sources in case the property is defined in a non-canonical place.
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }

            for (String propertyName : enumerable.getPropertyNames()) {
                if (propertyName == null || !propertyName.contains(TRUSTED_PACKAGES_KEY_FRAGMENT)) {
                    continue;
                }

                Object rawValue = enumerable.getProperty(propertyName);
                String value = rawValue == null ? null : String.valueOf(rawValue);
                if (containsWildcard(value)) {
                    offendingKeys.add(propertyName);
                }
            }
        }

        if (!offendingKeys.isEmpty()) {
            String message = "FATAL: Insecure Kafka JsonDeserializer trusted packages configuration detected in prod. "
                    + "Remove '*' from spring.json.trusted.packages and explicitly list allowed DTO packages. "
                    + "Offending properties: " + String.join(", ", offendingKeys);
            log.fatal(message);
            throw new IllegalStateException(message);
        }
    }

    private static boolean containsWildcard(String trustedPackagesValue) {
        if (trustedPackagesValue == null) {
            return false;
        }

        String normalized = trustedPackagesValue.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        // Support comma-separated values.
        for (String token : normalized.split(",")) {
            String trimmed = token == null ? "" : token.trim();
            if (Objects.equals(trimmed, "*")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
