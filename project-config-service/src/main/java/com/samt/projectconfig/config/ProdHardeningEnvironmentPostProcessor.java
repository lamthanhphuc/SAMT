package com.samt.projectconfig.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.Profiles;

public class ProdHardeningEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "projectConfigServiceProdHardening";

    private static final String EXPOSURE_INCLUDE_KEY = "management.endpoints.web.exposure.include";
    private static final String HEALTH_SHOW_DETAILS_KEY = "management.endpoint.health.show-details";

    private static final String GRPC_SERVER_SECURITY_ENABLED_KEY = "grpc.server.security.enabled";
    private static final String GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_CAMEL = "grpc.server.security.clientAuth";
    private static final String GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_KEBAB = "grpc.server.security.client-auth";

    private static final Set<String> ALLOWED_EXPOSED_ENDPOINTS = Set.of("health", "info");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        failFastIfDisallowedOverride(environment);
        failFastIfGrpcPlaintextConfigured(environment);

        Map<String, Object> enforced = Map.of(
                EXPOSURE_INCLUDE_KEY, "health,info",
                HEALTH_SHOW_DETAILS_KEY, "never",
            "management.server.add-application-context-header", "false",
            "grpc.client.default.negotiation-type", "TLS",
            GRPC_SERVER_SECURITY_ENABLED_KEY, "true",
            GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_CAMEL, "REQUIRE"
        );

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, enforced));
    }

    private static void failFastIfDisallowedOverride(ConfigurableEnvironment environment) {
        String exposureInclude = environment.getProperty(EXPOSURE_INCLUDE_KEY);
        if (exposureInclude != null) {
            Set<String> includeSet = parseCsvToLowercaseSet(exposureInclude);

            if (includeSet.contains("*")) {
                throw new IllegalStateException("FATAL: In prod, '" + EXPOSURE_INCLUDE_KEY + "' must not include wildcard '*'. Actual: " + exposureInclude);
            }

            if (!includeSet.equals(ALLOWED_EXPOSED_ENDPOINTS)) {
                throw new IllegalStateException(
                        "FATAL: In prod, '" + EXPOSURE_INCLUDE_KEY + "' must be exactly 'health,info'. Actual: " + exposureInclude);
            }
        }

        String showDetails = environment.getProperty(HEALTH_SHOW_DETAILS_KEY);
        if (showDetails != null) {
            String normalized = showDetails.trim().toLowerCase(Locale.ROOT);
            if (!"never".equals(normalized)) {
                throw new IllegalStateException(
                        "FATAL: In prod, '" + HEALTH_SHOW_DETAILS_KEY + "' must be 'never'. Actual: " + showDetails);
            }
        }
    }

    private static void failFastIfGrpcPlaintextConfigured(ConfigurableEnvironment environment) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (PROPERTY_SOURCE_NAME.equals(propertySource.getName())) {
                continue;
            }
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }

            for (String propertyName : enumerable.getPropertyNames()) {
                String keyLower = propertyName.toLowerCase(Locale.ROOT);
                if (isGrpcClientNegotiationTypeKey(keyLower)) {
                    Object value = enumerable.getProperty(propertyName);
                    if (value != null && "plaintext".equals(value.toString().trim().toLowerCase(Locale.ROOT))) {
                        throw new IllegalStateException(
                                "FATAL: gRPC plaintext negotiation is forbidden in prod. Found '" + propertyName + "=plaintext' in property source '" + propertySource.getName() + "'.");
                    }
                }
                if (GRPC_SERVER_SECURITY_ENABLED_KEY.equals(keyLower)) {
                    Object value = enumerable.getProperty(propertyName);
                    if (value != null && "false".equals(value.toString().trim().toLowerCase(Locale.ROOT))) {
                        throw new IllegalStateException(
                                "FATAL: gRPC server security must not be disabled in prod. Found '" + propertyName + "=false' in property source '" + propertySource.getName() + "'.");
                    }
                }
                if (GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_CAMEL.toLowerCase(Locale.ROOT).equals(keyLower)
                        || GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_KEBAB.equals(keyLower)) {
                    Object value = enumerable.getProperty(propertyName);
                    if (value != null && !"require".equals(value.toString().trim().toLowerCase(Locale.ROOT))) {
                        throw new IllegalStateException(
                                "FATAL: gRPC server must require mTLS in prod. Found '" + propertyName + "=" + value + "' in property source '" + propertySource.getName() + "'.");
                    }
                }
            }
        }

        String enabled = environment.getProperty(GRPC_SERVER_SECURITY_ENABLED_KEY);
        if (enabled != null && !"true".equals(enabled.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "FATAL: In prod, '" + GRPC_SERVER_SECURITY_ENABLED_KEY + "' must be true. Actual: " + enabled);
        }

        String clientAuth = environment.getProperty(GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_CAMEL);
        if (clientAuth == null) {
            clientAuth = environment.getProperty(GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_KEBAB);
        }
        if (clientAuth != null && !"require".equals(clientAuth.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "FATAL: In prod, gRPC server 'clientAuth' must be REQUIRE. Actual: " + clientAuth);
        }
    }

    private static boolean isGrpcClientNegotiationTypeKey(String keyLower) {
        if (!keyLower.startsWith("grpc.client.")) {
            return false;
        }
        return keyLower.endsWith(".negotiation-type") || keyLower.endsWith(".negotiationtype");
    }

    private static Set<String> parseCsvToLowercaseSet(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
