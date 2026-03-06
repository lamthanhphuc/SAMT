package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

    @Bean
    @Profile("prod")
    public SecurityWebFilterChain springSecurityFilterChainProd(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/.well-known/jwks.json").permitAll()
                        .pathMatchers("/.well-known/internal-jwks.json").permitAll()
                        .pathMatchers(
                                "/api/identity/register",
                                "/api/identity/login",
                                "/api/identity/refresh-token",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .pathMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/identity/v3/api-docs/**",
                                "/user-group/v3/api-docs/**",
                                "/project-config/v3/api-docs/**",
                                "/sync/v3/api-docs/**",
                                "/analysis/v3/api-docs/**",
                                "/report/v3/api-docs/**",
                                "/notification/v3/api-docs/**"
                        ).permitAll()
                        .anyExchange().authenticated())
                .build();
    }

    @Bean
    @Profile("!prod")
    public SecurityWebFilterChain springSecurityFilterChainNonProd(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/.well-known/jwks.json").permitAll()
                        .pathMatchers("/.well-known/internal-jwks.json").permitAll()
                        .pathMatchers(
                                "/api/identity/register",
                                "/api/identity/login",
                                "/api/identity/refresh-token",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                // Gateway's own Swagger UI
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/identity/v3/api-docs/**",
                                "/user-group/v3/api-docs/**",
                                "/project-config/v3/api-docs/**",
                                "/sync/v3/api-docs/**",
                                "/analysis/v3/api-docs/**",
                                "/report/v3/api-docs/**",
                                "/notification/v3/api-docs/**",
                                "/test/**"
                        ).permitAll()
                        .anyExchange().authenticated())
                .build();
    }
}
