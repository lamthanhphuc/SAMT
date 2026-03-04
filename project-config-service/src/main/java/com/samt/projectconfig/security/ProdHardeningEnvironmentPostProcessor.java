package com.samt.projectconfig.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Production hardening guard for Actuator exposure.
 *
 * <p>Intentional fail-fast behavior: if production profile is active and
 * actuator exposure is unsafe, the application will refuse to start.
 */
public final class ProdHardeningEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ProdHardeningEnvironmentPostProcessor.class);

    private static final String PROD_PROFILE = "prod";
    private static final String EXPOSURE_INCLUDE_KEY = "management.endpoints.web.exposure.include";
    private static final String HEALTH_SHOW_DETAILS_KEY = "management.endpoint.health.show-details";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            return;
        }

        Set<String> include = parseCsvToLowerSet(environment.getProperty(EXPOSURE_INCLUDE_KEY));

        // In production we require ONLY 'health' exposure.
        boolean hasWildcard = include.contains("*");
        boolean includesPrometheus = include.contains("prometheus");
        boolean includesMetrics = include.contains("metrics");
        boolean onlyHealth = include.size() == 1 && include.contains("health");
        boolean includeMissingOrEmpty = include.isEmpty();

        if (hasWildcard || includesPrometheus || includesMetrics || includeMissingOrEmpty || !onlyHealth) {
            String msg = "Unsafe Actuator exposure detected in prod. "
                    + EXPOSURE_INCLUDE_KEY + " must be exactly 'health' (no metrics/prometheus/wildcards). "
                    + "Effective value='" + safeValue(environment.getProperty(EXPOSURE_INCLUDE_KEY)) + "'.";
            logger.error("FATAL: {}", msg);
            throw new IllegalStateException(msg);
        }

        String showDetails = safeValue(environment.getProperty(HEALTH_SHOW_DETAILS_KEY));
        if (!"never".equalsIgnoreCase(showDetails)) {
            String msg = "Unsafe Actuator health details setting in prod. "
                    + HEALTH_SHOW_DETAILS_KEY + " must be 'never'. "
                    + "Effective value='" + showDetails + "'.";
            logger.error("FATAL: {}", msg);
            throw new IllegalStateException(msg);
        }
    }

    private static Set<String> parseCsvToLowerSet(String raw) {
        if (raw == null) {
            return Collections.emptySet();
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(trimmed.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .forEach(values::add);
        return values;
    }

    private static String safeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
