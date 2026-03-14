package com.example.gateway.config;

import com.example.gateway.error.GatewayErrorResponseWriter;
import com.example.gateway.security.ExchangeAttributeSecurityContextRepository;
import com.example.gateway.security.PublicEndpointPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.firewall.ServerWebExchangeFirewall;
import org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall;

import java.util.List;

@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

        @Bean
        public ServerSecurityContextRepository serverSecurityContextRepository() {
                return new ExchangeAttributeSecurityContextRepository();
        }

        @Bean
        public ServerWebExchangeFirewall serverWebExchangeFirewall() {
                StrictServerWebExchangeFirewall firewall = new StrictServerWebExchangeFirewall();
                firewall.setAllowedHttpMethods(List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.valueOf("QUERY")));
                return firewall;
        }

    @Bean
    @Profile("prod")
        public SecurityWebFilterChain springSecurityFilterChainProd(
                ServerHttpSecurity http,
                GatewayErrorResponseWriter errorResponseWriter,
                ServerSecurityContextRepository serverSecurityContextRepository
        ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                                .securityContextRepository(serverSecurityContextRepository)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> errorResponseWriter.write(exchange, 401, "Unauthorized", "Unauthorized"))
                        .accessDeniedHandler((exchange, denied) -> errorResponseWriter.write(exchange, 403, "Forbidden", "Forbidden")))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/__gateway/fallback/**").permitAll()
                        .pathMatchers("/.well-known/jwks.json").permitAll()
                        .pathMatchers("/.well-known/internal-jwks.json").permitAll()
                        .pathMatchers(PublicEndpointPaths.SWAGGER_WHITELIST).permitAll()
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
        public SecurityWebFilterChain springSecurityFilterChainNonProd(
                ServerHttpSecurity http,
                GatewayErrorResponseWriter errorResponseWriter,
                ServerSecurityContextRepository serverSecurityContextRepository
        ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                                .securityContextRepository(serverSecurityContextRepository)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> errorResponseWriter.write(exchange, 401, "Unauthorized", "Unauthorized"))
                        .accessDeniedHandler((exchange, denied) -> errorResponseWriter.write(exchange, 403, "Forbidden", "Forbidden")))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/__gateway/fallback/**").permitAll()
                        .pathMatchers("/.well-known/jwks.json").permitAll()
                        .pathMatchers("/.well-known/internal-jwks.json").permitAll()
                        .pathMatchers(PublicEndpointPaths.SWAGGER_WHITELIST).permitAll()
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
