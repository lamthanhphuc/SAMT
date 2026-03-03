package com.example.gateway;

import com.example.gateway.filter.JwtAuthenticationFilter;
import com.example.gateway.security.JwtAudienceValidator;
import com.example.gateway.security.JwtTokenTypeValidator;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebHandler;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

class JwtClaimsValidationWebTestClientTest {

    private static final String EXPECTED_ISSUER = "identity-service";
    private static final String EXPECTED_AUDIENCE = "api-gateway";

    @Test
    void wrongIssuer_returns401() throws Exception {
        TestHarness harness = TestHarness.create();

        String token = harness.mintToken(
                "other-issuer",
                Collections.singletonList(EXPECTED_AUDIENCE),
                "ACCESS"
        );

        harness.client.get()
                .uri("/api/groups/secure-resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void missingAudience_returns401() throws Exception {
        TestHarness harness = TestHarness.create();

        String token = harness.mintToken(
                EXPECTED_ISSUER,
                null,
                "ACCESS"
        );

        harness.client.get()
                .uri("/api/groups/secure-resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void wrongAudience_returns401() throws Exception {
        TestHarness harness = TestHarness.create();

        String token = harness.mintToken(
                EXPECTED_ISSUER,
                Collections.singletonList("some-other-service"),
                "ACCESS"
        );

        harness.client.get()
                .uri("/api/groups/secure-resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void nonAccessTokenType_returns401() throws Exception {
        TestHarness harness = TestHarness.create();

        String token = harness.mintToken(
                EXPECTED_ISSUER,
                Collections.singletonList(EXPECTED_AUDIENCE),
                "REFRESH"
        );

        harness.client.get()
                .uri("/api/groups/secure-resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validAccessToken_returns200() throws Exception {
        TestHarness harness = TestHarness.create();

        String token = harness.mintToken(
                EXPECTED_ISSUER,
                Collections.singletonList(EXPECTED_AUDIENCE),
                "ACCESS"
        );

        harness.client.get()
                .uri("/api/groups/secure-resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK);
    }

    private static final class TestHarness {
        private final RSAPrivateKey privateKey;
        private final WebTestClient client;

        private TestHarness(RSAPrivateKey privateKey, WebTestClient client) {
            this.privateKey = privateKey;
            this.client = client;
        }

        static TestHarness create() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                    .withPublicKey(publicKey)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();

            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(EXPECTED_ISSUER);
            OAuth2TokenValidator<Jwt> audienceValidator = new JwtAudienceValidator(EXPECTED_AUDIENCE);
            OAuth2TokenValidator<Jwt> tokenTypeValidator = new JwtTokenTypeValidator("ACCESS");
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator, tokenTypeValidator));

            MockEnvironment env = new MockEnvironment();
            JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(decoder, env);

            WebHandler terminalOk = exchange -> {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return exchange.getResponse().setComplete();
            };

            WebTestClient client = WebTestClient
                    .bindToWebHandler(terminalOk)
                    .webFilter(jwtFilter)
                    .build();
            return new TestHarness(privateKey, client);
        }

        String mintToken(String issuer, List<String> audience, String tokenType) throws Exception {
            Instant now = Instant.now();

            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .jwtID(UUID.randomUUID().toString())
                    .subject("123")
                    .issuer(issuer)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(120)))
                    .claim("roles", Collections.singletonList("ADMIN"))
                    .claim("token_type", tokenType);

            if (audience != null) {
                claims.audience(audience);
            }

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID("test-kid")
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims.build());
            JWSSigner signer = new RSASSASigner(privateKey);
            jwt.sign(signer);
            return jwt.serialize();
        }
    }
}
