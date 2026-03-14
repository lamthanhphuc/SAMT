package com.samt.projectconfig.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.Profiles;

public class ProdGrpcSecurityEnforcerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ProdGrpcSecurityEnforcerEnvironmentPostProcessor.class);

    private static final String GRPC_SERVER_SECURITY_ENABLED_KEY = "grpc.server.security.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        List<String> violations = new ArrayList<>();

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }

            for (String propertyName : enumerable.getPropertyNames()) {
                String keyLower = propertyName.toLowerCase(Locale.ROOT);
                String compactKey = compactKey(keyLower);

                if (isGrpcClientNegotiationTypeKey(compactKey)) {
                    Object value = enumerable.getProperty(propertyName);
                    if (isPlaintext(value)) {
                        violations.add("gRPC plaintext negotiation is forbidden in prod. Found '" + propertyName + "=" + value + "' in property source '" + propertySource.getName() + "'.");
                    }
                }

                if (GRPC_SERVER_SECURITY_ENABLED_KEY.equals(keyLower) || "grpcserversecurityenabled".equals(compactKey)) {
                    Object value = enumerable.getProperty(propertyName);
                    if (isFalse(value)) {
                        violations.add("gRPC server security must not be disabled in prod. Found '" + propertyName + "=" + value + "' in property source '" + propertySource.getName() + "'.");
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            for (String violation : violations) {
                log.error("FATAL: {}", violation);
            }
            throw new IllegalStateException("FATAL: gRPC security policy violations detected in prod; refusing to start. Count=" + violations.size());
        }
    }

    private static boolean isGrpcClientNegotiationTypeKey(String compactKey) {
        return compactKey.startsWith("grpcclient") && compactKey.endsWith("negotiationtype");
    }

    private static boolean isPlaintext(Object value) {
        if (value == null) {
            return false;
        }
        return "plaintext".equals(value.toString().trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isFalse(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        return "false".equals(value.toString().trim().toLowerCase(Locale.ROOT));
    }

    private static String compactKey(String keyLower) {
        return keyLower.replaceAll("[^a-z0-9]", "");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
