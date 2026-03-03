package com.example.gateway.config;

import com.example.gateway.security.JwtAudienceValidator;
import com.example.gateway.security.JwtTokenTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${jwt.jwks-uri}") String jwksUri,
            @Value("${jwt.expected-issuer:identity-service}") String expectedIssuer,
            @Value("${jwt.expected-audience:api-gateway}") String expectedAudience
    ) {
        if (!StringUtils.hasText(jwksUri)) {
            throw new IllegalStateException("JWT_JWKS_URI must be set (non-blank).");
        }

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(expectedIssuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtAudienceValidator(expectedAudience);
        OAuth2TokenValidator<Jwt> tokenTypeValidator = new JwtTokenTypeValidator("ACCESS");

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator, tokenTypeValidator));
        return decoder;
    }
}
