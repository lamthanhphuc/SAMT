package com.example.gateway;

import com.example.gateway.filter.JwtAuthenticationFilter;
import com.example.gateway.filter.RedisRateLimitGatewayFilter;
import com.example.gateway.filter.SignedHeaderFilter;
import com.example.gateway.util.JwtUtil;
import com.example.gateway.util.SignedHeaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class RouteFilterOrderIntegrationTest {

    @Test
    void order_jwt_before_signed_header() {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(mock(JwtUtil.class));
        SignedHeaderFilter signedHeaderFilter = new SignedHeaderFilter(mock(SignedHeaderUtil.class));

        assertThat(jwtAuthenticationFilter.getOrder()).isEqualTo(-2147483638); // HIGHEST_PRECEDENCE + 10
        assertThat(signedHeaderFilter.getOrder()).isEqualTo(-50);
        assertThat(jwtAuthenticationFilter.getOrder()).isLessThan(signedHeaderFilter.getOrder());
    }

    @Test
    void authFail_withoutToken_returns401_and_stops_chain() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtUtil);
        SignedHeaderFilter signedHeaderFilter = new SignedHeaderFilter(mock(SignedHeaderUtil.class));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/groups/secure-resource").build()
        );

        AtomicBoolean signedHeaderReached = new AtomicBoolean(false);

        WebFilterChain terminal = currentExchange -> {
            signedHeaderReached.set(true);
            return Mono.empty();
        };

        jwtAuthenticationFilter.filter(exchange, currentExchange -> signedHeaderFilter.filter(currentExchange, terminal)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(signedHeaderReached.get()).isFalse();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Internal-Signature")).isNull();
        verify(jwtUtil, never()).validateAndParseClaims(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registerRateLimit_filter_exists_and_is_independent() {
        RedisRateLimiter globalLimiter = new RedisRateLimiter(100, 200, 1);
        RedisRateLimiter loginLimiter = new RedisRateLimiter(5, 10, 60);
        KeyResolver keyResolver = exchange -> Mono.just("127.0.0.1");

        RedisRateLimitGatewayFilter filter = new RedisRateLimitGatewayFilter(
                globalLimiter,
                loginLimiter,
                keyResolver
        );

        GatewayFilter loginFilter = filter.loginRateLimit("identity-login");
        GatewayFilter registerFilter = filter.registerRateLimit("identity-register");
        GatewayFilter globalFilter = filter.globalRateLimit("identity-service");

        assertThat(loginFilter).isNotNull();
        assertThat(registerFilter).isNotNull();
        assertThat(globalFilter).isNotNull();
        assertThat(registerFilter).isNotSameAs(loginFilter);
    }
}
