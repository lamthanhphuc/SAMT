package com.example.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;

/**
 * Redis-backed JTI replay prevention for internal JWT tokens.
 * Rejects any token whose jti has already been seen within the TTL window.
 */
public class JtiReplayValidator implements OAuth2TokenValidator<Jwt> {

    private static final String KEY_PREFIX = "jti:";
    private static final OAuth2Error REPLAY_ERROR =
            new OAuth2Error("invalid_token", "JWT replay detected (duplicate jti)", null);
        private static final OAuth2Error REPLAY_UNVERIFIED_ERROR =
            new OAuth2Error("invalid_token", "Unable to verify JWT replay protection", null);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    /**
     * @param redisTemplate active Redis connection
     * @param ttl           how long to remember a jti (token TTL + clock skew)
     */
    public JtiReplayValidator(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jti = token.getClaimAsString("jti");
        if (jti == null || jti.isBlank()) {
            // jti presence is enforced by the existing jtiRequiredValidator; skip here
            return OAuth2TokenValidatorResult.success();
        }

        String key = KEY_PREFIX + jti;
        // SET key 1 EX <ttl> NX — returns true only if the key did NOT already exist
        Boolean firstSeen = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);

        if (firstSeen == null) {
            return OAuth2TokenValidatorResult.failure(REPLAY_UNVERIFIED_ERROR);
        }

        if (Boolean.FALSE.equals(firstSeen)) {
            return OAuth2TokenValidatorResult.failure(REPLAY_ERROR);
        }

        return OAuth2TokenValidatorResult.success();
    }
}
