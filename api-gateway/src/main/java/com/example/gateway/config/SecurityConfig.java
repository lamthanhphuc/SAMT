package com.example.gateway.config;

import com.example.gateway.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security Configuration for API Gateway
 * - JWT validation tại Gateway với proper filter integration
 * - Public endpoints không cần authentication
 * - JWT filter chạy trước authorization để set SecurityContext
 */
@EnableWebFluxSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()
                )

                // Configure security headers
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(contentTypeOptions -> contentTypeOptions.and())
                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                .maxAgeInSeconds(31536000) // 1 year
                                .includeSubdomains(true)
                        )
                        .referrerPolicy(referrerPolicy -> 
                                referrerPolicy.policy(org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        .contentSecurityPolicy("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")
                )

                // Integrate CORS configuration directly into SecurityConfig
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Add JWT filter before AUTHENTICATION to set SecurityContext
                .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(
                                "/api/identity/login",
                                "/api/identity/register",
                                "/api/identity/refresh-token"
                        ).permitAll()
                        // Actuator endpoints security: health public, all others require auth
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/actuator/**").authenticated()
                        // All other endpoints require authentication
                        // Swagger UI requires authentication  
                        .anyExchange().authenticated()
                )

                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Get allowed origins from environment variable with validation and secure fallback
        String origins = System.getenv("CORS_ALLOWED_ORIGINS");
        List<String> validatedOrigins;
        
        if (origins == null || origins.isEmpty()) {
            // Secure fallback: only localhost for development
            validatedOrigins = Arrays.asList("http://localhost:3000", "https://localhost:3000");
            System.err.println("WARNING: CORS_ALLOWED_ORIGINS not set. Using secure localhost fallback. Set CORS_ALLOWED_ORIGINS for production.");
        } else {
            // Validate each origin format and reject wildcards
            validatedOrigins = Arrays.stream(origins.split(","))
                    .map(String::trim)
                    .filter(this::isValidOrigin)
                    .toList();
            
            if (validatedOrigins.isEmpty()) {
                // All origins were invalid, use secure fallback
                validatedOrigins = Arrays.asList("http://localhost:3000", "https://localhost:3000");
                System.err.println("WARNING: All CORS origins were invalid. Using secure localhost fallback.");
            }
        }
        
        configuration.setAllowedOriginPatterns(validatedOrigins);
        
        // Configure CORS policy
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-User-Id", "X-User-Role", "X-Request-ID"
        ));
        configuration.setExposedHeaders(Arrays.asList(
            "X-User-Id", "X-User-Role", "X-Request-ID"
        ));
        configuration.setAllowCredentials(false); // Security: no credentials
        configuration.setMaxAge(3600L); // 1 hour preflight cache
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    /**
     * Validates CORS origin format to prevent wildcards and ensure proper URL format
     */
    private boolean isValidOrigin(String origin) {
        // Reject wildcards
        if ("*".equals(origin) || origin.contains("*")) {
            System.err.println("WARNING: Wildcard origin rejected: " + origin);
            return false;
        }
        
        // Must have scheme and valid URL format
        if (!origin.startsWith("http://") && !origin.startsWith("https://")) {
            System.err.println("WARNING: Invalid origin format (missing scheme): " + origin);
            return false;
        }
        
        // Basic URL validation
        try {
            new java.net.URL(origin);
            return true;
        } catch (java.net.MalformedURLException e) {
            System.err.println("WARNING: Invalid origin URL format: " + origin);
            return false;
        }
    }
}