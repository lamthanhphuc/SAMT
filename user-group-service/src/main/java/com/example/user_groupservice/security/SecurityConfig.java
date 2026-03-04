package com.example.user_groupservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Security configuration for the User & Group Service.
 * Uses stateless JWT authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(InternalJwtValidationProperties.class)
public class SecurityConfig {
    
    private final JwtAuthenticationEntryPoint authEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    
    @Bean
    @Profile("!prod")
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            // Disable CSRF (stateless API)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure authorization
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                // Swagger UI
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // Stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Exception handling
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(this::jwtToAuthentication)
                )
            );
        
        return http.build();
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain securityFilterChainProd(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**"
                ).permitAll()
                .requestMatchers("/actuator/info").authenticated()
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(this::jwtToAuthentication)
                )
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
        InternalJwtValidationProperties internalJwtValidationProperties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> serviceClaimValidator = new JwtClaimValidator<>(
            "service",
            service -> Objects.equals(internalJwtValidationProperties.getExpectedService(), service)
        );

        OAuth2TokenValidator<Jwt> jtiRequiredValidator = token -> {
            String jti = token.getClaimAsString("jti");
            if (jti == null || jti.isBlank()) {
                OAuth2Error error = new OAuth2Error("invalid_token", "Missing required jti claim", null);
                return OAuth2TokenValidatorResult.failure(error);
            }
            return OAuth2TokenValidatorResult.success();
        };

        OAuth2TokenValidator<Jwt> kidRequiredValidator = token -> {
            Object kid = token.getHeaders().get("kid");
            if (kid == null || kid.toString().isBlank()) {
                OAuth2Error error = new OAuth2Error("invalid_token", "Missing required kid header", null);
                return OAuth2TokenValidatorResult.failure(error);
            }
            return OAuth2TokenValidatorResult.success();
        };

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(
            Duration.ofSeconds(internalJwtValidationProperties.getClockSkewSeconds())
        );

        DelegatingOAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(internalJwtValidationProperties.getIssuer()),
            timestampValidator,
            serviceClaimValidator,
            jtiRequiredValidator,
            kidRequiredValidator
        );

        decoder.setJwtValidator(validator);
        return decoder;
    }

    private UsernamePasswordAuthenticationToken jwtToAuthentication(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new org.springframework.security.oauth2.jwt.JwtException("Invalid sub claim");
        }

        Collection<? extends GrantedAuthority> authorities = extractAuthorities(jwt);
        CurrentUser currentUser = new CurrentUser(userId, authorities);
        return new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .toList();
    }
}
