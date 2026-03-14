package com.example.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JtiReplayValidatorTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @Test
    void acceptsFirstSeenJti() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("jti:abc-123"), eq("1"), any(Duration.class))).thenReturn(true);

        JtiReplayValidator validator = new JtiReplayValidator(redisTemplate, Duration.ofSeconds(60));
        Jwt jwt = jwtWithJti("abc-123");

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsDuplicateJti() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("jti:dup"), eq("1"), any(Duration.class))).thenReturn(false);

        JtiReplayValidator validator = new JtiReplayValidator(redisTemplate, Duration.ofSeconds(60));
        Jwt jwt = jwtWithJti("dup");

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWhenReplayStateCannotBeVerified() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("jti:unknown"), eq("1"), any(Duration.class))).thenReturn(null);

        JtiReplayValidator validator = new JtiReplayValidator(redisTemplate, Duration.ofSeconds(60));
        Jwt jwt = jwtWithJti("unknown");

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void skipsValidationWhenJtiMissing() {
        JtiReplayValidator validator = new JtiReplayValidator(redisTemplate, Duration.ofSeconds(60));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(claims -> claims.putAll(Map.of("service", "api-gateway")))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void skipsValidationWhenJtiBlank() {
        JtiReplayValidator validator = new JtiReplayValidator(redisTemplate, Duration.ofSeconds(60));
        Jwt jwt = jwtWithJti("   ");

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    private Jwt jwtWithJti(String jti) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("jti", jti)
                .build();
    }
}
