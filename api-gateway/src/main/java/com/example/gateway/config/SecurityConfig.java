package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.firewall.ServerWebExchangeFirewall;
import org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall;

import java.util.List;

@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

        @Bean
        public ServerWebExchangeFirewall serverWebExchangeFirewall() {
                StrictServerWebExchangeFirewall firewall = new StrictServerWebExchangeFirewall();
                firewall.setAllowedHttpMethods(List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.valueOf("QUERY")));
                return firewall;
        }

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
                        .pathMatchers("/identity/v3/api-docs/**", "/identity/v3/api-docs").permitAll()
                        .pathMatchers("/user-group/v3/api-docs/**", "/user-group/v3/api-docs").permitAll()
                        .pathMatchers("/project-config/v3/api-docs/**", "/project-config/v3/api-docs").permitAll()
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/actuator/health"
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
                        .pathMatchers("/identity/v3/api-docs/**", "/identity/v3/api-docs").permitAll()
                        .pathMatchers("/user-group/v3/api-docs/**", "/user-group/v3/api-docs").permitAll()
                        .pathMatchers("/project-config/v3/api-docs/**", "/project-config/v3/api-docs").permitAll()
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/actuator/health"
                        ).permitAll()
                        .anyExchange().authenticated())
                .build();
    }
}
