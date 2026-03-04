package com.example.user_groupservice.config;

import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.Profiles;

public class ProdHardeningEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "userGroupServiceProdHardening";

    private static final String GRPC_SERVER_SECURITY_ENABLED_KEY = "grpc.server.security.enabled";
    private static final String GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_CAMEL = "grpc.server.security.clientAuth";
    private static final String GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_KEBAB = "grpc.server.security.client-auth";

    private static final String GRPC_CLIENT_DEFAULT_NEGOTIATION_TYPE_KEY = "grpc.client.default.negotiation-type";

    private static final String GRPC_SERVER_CERT_CHAIN_KEY_CAMEL = "grpc.server.security.certificateChain";
    private static final String GRPC_SERVER_CERT_CHAIN_KEY_KEBAB = "grpc.server.security.certificate-chain";
    private static final String GRPC_SERVER_PRIVATE_KEY_KEY_CAMEL = "grpc.server.security.privateKey";
    private static final String GRPC_SERVER_PRIVATE_KEY_KEY_KEBAB = "grpc.server.security.private-key";
    private static final String GRPC_SERVER_TRUST_CERT_KEY_CAMEL = "grpc.server.security.trustCertCollection";
    private static final String GRPC_SERVER_TRUST_CERT_KEY_KEBAB = "grpc.server.security.trust-cert-collection";

    private static final String GRPC_CLIENT_TRUST_CERT_KEY_CAMEL = "grpc.client.default.security.trustCertCollection";
    private static final String GRPC_CLIENT_TRUST_CERT_KEY_KEBAB = "grpc.client.default.security.trust-cert-collection";
    private static final String GRPC_CLIENT_CERT_CHAIN_KEY_CAMEL = "grpc.client.default.security.certificateChain";
    private static final String GRPC_CLIENT_CERT_CHAIN_KEY_KEBAB = "grpc.client.default.security.certificate-chain";
    private static final String GRPC_CLIENT_PRIVATE_KEY_KEY_CAMEL = "grpc.client.default.security.privateKey";
    private static final String GRPC_CLIENT_PRIVATE_KEY_KEY_KEBAB = "grpc.client.default.security.private-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        failFastIfGrpcPlaintextConfigured(environment);
        enforceRequiredGrpcTlsAndMtlsProperties(environment);

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, java.util.Map.of(
                GRPC_CLIENT_DEFAULT_NEGOTIATION_TYPE_KEY, "TLS",
                GRPC_SERVER_SECURITY_ENABLED_KEY, "true",
                GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_CAMEL, "REQUIRE"
        )));
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
    }

    private static void enforceRequiredGrpcTlsAndMtlsProperties(ConfigurableEnvironment environment) {
        String negotiationType = environment.getProperty(GRPC_CLIENT_DEFAULT_NEGOTIATION_TYPE_KEY);
        if (negotiationType != null && "plaintext".equals(negotiationType.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "FATAL: In prod, '" + GRPC_CLIENT_DEFAULT_NEGOTIATION_TYPE_KEY + "' must not be plaintext.");
        }

        String serverSecurityEnabled = environment.getProperty(GRPC_SERVER_SECURITY_ENABLED_KEY);
        if (serverSecurityEnabled == null || !"true".equals(serverSecurityEnabled.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "FATAL: In prod, '" + GRPC_SERVER_SECURITY_ENABLED_KEY + "' must be true.");
        }

        String clientAuth = firstNonBlank(
                environment.getProperty(GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_CAMEL),
                environment.getProperty(GRPC_SERVER_SECURITY_CLIENT_AUTH_KEY_KEBAB)
        );
        if (clientAuth == null || !"require".equals(clientAuth.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "FATAL: In prod, gRPC server must require client certs (clientAuth=REQUIRE).");
        }

        requireNonBlank(environment,
                "FATAL: In prod, gRPC server certificateChain is required.",
                GRPC_SERVER_CERT_CHAIN_KEY_CAMEL,
                GRPC_SERVER_CERT_CHAIN_KEY_KEBAB);
        requireNonBlank(environment,
                "FATAL: In prod, gRPC server privateKey is required.",
                GRPC_SERVER_PRIVATE_KEY_KEY_CAMEL,
                GRPC_SERVER_PRIVATE_KEY_KEY_KEBAB);
        requireNonBlank(environment,
                "FATAL: In prod, gRPC server trustCertCollection is required for mTLS.",
                GRPC_SERVER_TRUST_CERT_KEY_CAMEL,
                GRPC_SERVER_TRUST_CERT_KEY_KEBAB);

        requireNonBlank(environment,
                "FATAL: In prod, gRPC client trustCertCollection is required for server identity verification.",
                GRPC_CLIENT_TRUST_CERT_KEY_CAMEL,
                GRPC_CLIENT_TRUST_CERT_KEY_KEBAB);
        requireNonBlank(environment,
                "FATAL: In prod, gRPC client certificateChain is required for mTLS.",
                GRPC_CLIENT_CERT_CHAIN_KEY_CAMEL,
                GRPC_CLIENT_CERT_CHAIN_KEY_KEBAB);
        requireNonBlank(environment,
                "FATAL: In prod, gRPC client privateKey is required for mTLS.",
                GRPC_CLIENT_PRIVATE_KEY_KEY_CAMEL,
                GRPC_CLIENT_PRIVATE_KEY_KEY_KEBAB);
    }

    private static void requireNonBlank(ConfigurableEnvironment environment, String message, String... keys) {
        String value = null;
        for (String key : keys) {
            String candidate = environment.getProperty(key);
            if (candidate != null && !candidate.isBlank()) {
                value = candidate;
                break;
            }
        }
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean isGrpcClientNegotiationTypeKey(String keyLower) {
        if (!keyLower.startsWith("grpc.client.")) {
            return false;
        }
        return keyLower.endsWith(".negotiation-type") || keyLower.endsWith(".negotiationtype");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
