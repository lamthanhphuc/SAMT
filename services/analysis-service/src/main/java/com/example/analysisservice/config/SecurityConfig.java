package com.example.analysisservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(InternalJwtValidationProperties.class)
public class SecurityConfig {

    private final InternalJwtValidationProperties jwtProps;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/internal/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(jwtProps.getIssuer());
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(jwtProps.getClockSkewSeconds()));

        OAuth2TokenValidator<Jwt> serviceValidator = token -> validateRequiredClaim(
                "service",
                jwtProps.getExpectedService().equals(token.getClaimAsString("service")),
                "Invalid service claim"
        );

        OAuth2TokenValidator<Jwt> jtiValidator = token -> validateRequiredClaim(
                "jti",
                token.getClaimAsString("jti") != null && !token.getClaimAsString("jti").isBlank(),
                "Missing jti claim"
        );

        OAuth2TokenValidator<Jwt> subValidator = token -> validateRequiredClaim(
                "sub",
                token.getSubject() != null && !token.getSubject().isBlank(),
                "Missing sub claim"
        );

        OAuth2TokenValidator<Jwt> rolesValidator = token -> {
            List<String> roles = token.getClaimAsStringList("roles");
            return validateRequiredClaim("roles", roles != null && !roles.isEmpty(), "Missing roles claim");
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                timestampValidator,
                serviceValidator,
                jtiValidator,
                subValidator,
                rolesValidator
        ));

        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return authorities;
        });
        return converter;
    }

    private OAuth2TokenValidatorResult validateRequiredClaim(String claim, boolean valid, String message) {
        if (!valid) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message + ": " + claim, null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
