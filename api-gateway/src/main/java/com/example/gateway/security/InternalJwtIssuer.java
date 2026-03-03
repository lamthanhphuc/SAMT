package com.example.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class InternalJwtIssuer {

    private final InternalJwtProperties props;
    private final RSASSASigner signer;

    public InternalJwtIssuer(InternalJwtProperties props, InternalJwtKeyMaterial keyMaterial) {
        this.props = props;
        this.signer = new RSASSASigner(keyMaterial.privateKey());
    }

    /**
     * Issues a new internal JWT based on a verified external JWT.
     */
    public String issueFromExternalJwt(Jwt externalJwt) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofSeconds(props.getTtlSeconds()));

        List<String> roles = externalJwt.getClaimAsStringList("roles");
        if (roles == null) {
            roles = List.of();
        }

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.getIssuer())
                .subject(externalJwt.getSubject())
                .claim("roles", roles)
                .claim("service", props.getServiceName())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(props.getKeyId())
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign internal JWT", e);
        }
        return jwt.serialize();
    }
}
