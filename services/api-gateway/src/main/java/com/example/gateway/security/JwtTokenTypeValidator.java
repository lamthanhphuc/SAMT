package com.example.gateway.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public final class JwtTokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private final String requiredTokenType;

    public JwtTokenTypeValidator(String requiredTokenType) {
        this.requiredTokenType = requiredTokenType;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String tokenType = token.getClaimAsString("token_type");
        if (!StringUtils.hasText(tokenType) || !requiredTokenType.equals(tokenType)) {
            OAuth2Error error = new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "Missing or invalid token_type",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
