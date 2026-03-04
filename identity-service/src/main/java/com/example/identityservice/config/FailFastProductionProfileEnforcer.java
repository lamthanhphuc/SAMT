package com.example.identityservice.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

public class FailFastProductionProfileEnforcer implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(FailFastProductionProfileEnforcer.class);

    private static final Set<String> PRODUCTION_VALUES = Set.of("prod", "production");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean productionEnvironment = isProductionEnvironment(environment);
        boolean prodProfileActive = environment.acceptsProfiles(Profiles.of("prod"));

        if (!productionEnvironment) {
            return;
        }

        String activeProfiles = String.join(",", environment.getActiveProfiles());
        if (activeProfiles.isBlank()) {
            activeProfiles = "<none>";
        }

        if (!prodProfileActive) {
            String message = "Production environment detected but 'prod' profile is NOT active. Aborting startup. "
                    + "Active profiles: " + activeProfiles + ". "
                    + "Set SPRING_PROFILES_ACTIVE=prod (or include 'prod' among active profiles).";

            // Log as early and loudly as possible (logging may not be fully initialized yet).
            try {
                log.error(message);
            } catch (Throwable ignored) {
                // ignore
            }
            System.err.println("FATAL: " + message);

            throw new IllegalStateException(message);
        }

        // Optional: visibility for operators.
        try {
            log.info("Production environment detected; active profiles: {}", Arrays.toString(environment.getActiveProfiles()));
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static boolean isProductionEnvironment(ConfigurableEnvironment environment) {
        // Environment variables are mapped as properties by Spring (e.g., ENV, DEPLOY_ENV).
        return isProdValue(environment.getProperty("ENV"))
                || isProdValue(environment.getProperty("DEPLOY_ENV"))
                || isProdValue(environment.getProperty("DEPLOYMENT_ENV"));
    }

    private static boolean isProdValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return PRODUCTION_VALUES.contains(normalized);
    }

    @Override
    public int getOrder() {
        // Run as early as possible, before other post-processors.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
